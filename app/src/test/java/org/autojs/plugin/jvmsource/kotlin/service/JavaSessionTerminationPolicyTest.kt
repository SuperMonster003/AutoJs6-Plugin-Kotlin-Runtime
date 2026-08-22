package org.autojs.plugin.jvmsource.kotlin.service

import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JavaSessionTerminationPolicyTest {
    @Test
    fun successCompileErrorRuntimeErrorAndWorkerDeathAreSingleTerminalOutcomes() {
        val outcomes = listOf(
            JavaSessionTerminationPolicy.Outcome.SUCCESS,
            JavaSessionTerminationPolicy.Outcome.COMPILATION_ERROR,
            JavaSessionTerminationPolicy.Outcome.RUNTIME_ERROR,
            JavaSessionTerminationPolicy.Outcome.WORKER_DIED,
        )
        outcomes.forEach { outcome ->
            val policy = JavaSessionTerminationPolicy()
            assertTrue(policy.tryTerminal(outcome))
            assertFalse(policy.tryTerminal(JavaSessionTerminationPolicy.Outcome.SUCCESS))
            assertEquals(outcome, policy.snapshot().outcome)
            assertTrue(policy.markCleaned())
            assertFalse(policy.markCleaned())
        }
    }

    @Test
    fun cancelInterruptsThenWaitsFiniteGraceBeforeOneHardStop() {
        val policy = JavaSessionTerminationPolicy()
        val plan = policy.requestCancellation(JvmCancellationReason.REQUESTED, workerLive = true)

        assertTrue(plan.accepted)
        assertTrue(plan.interruptCompiler)
        assertTrue(plan.signalWorker)
        assertTrue(plan.scheduleHardStop)
        assertFalse(plan.waitForDispatchCompletion)
        assertFalse(policy.tryTerminal(JavaSessionTerminationPolicy.Outcome.SUCCESS))
        assertFalse(policy.tryTerminal(JavaSessionTerminationPolicy.Outcome.RUNTIME_ERROR))
        assertTrue(policy.cancellationGraceExpired())
        assertFalse(policy.cancellationGraceExpired())
        assertTrue(policy.tryTerminal(JavaSessionTerminationPolicy.Outcome.CANCELLED))
    }

    @Test
    fun timeoutWinsLaterCancelAndWithoutWorkerNeedsNoHardStopGrace() {
        val policy = JavaSessionTerminationPolicy()
        val timeout = policy.requestCancellation(JvmCancellationReason.TIMEOUT, workerLive = false)
        val laterCancel = policy.requestCancellation(JvmCancellationReason.REQUESTED, workerLive = true)

        assertTrue(timeout.accepted)
        assertFalse(timeout.signalWorker)
        assertFalse(timeout.scheduleHardStop)
        assertFalse(timeout.waitForDispatchCompletion)
        assertFalse(laterCancel.accepted)
        assertEquals(JvmCancellationReason.TIMEOUT, laterCancel.reason)
        assertFalse(policy.cancellationGraceExpired())
        assertTrue(policy.tryTerminal(JavaSessionTerminationPolicy.Outcome.CANCELLED))
    }

    @Test
    fun hostDeathAndProviderDestroyAreTerminalAndRequireFreshSessionPolicy() {
        listOf(
            JavaSessionTerminationPolicy.Outcome.HOST_DIED,
            JavaSessionTerminationPolicy.Outcome.PROVIDER_DESTROYED,
        ).forEach { outcome ->
            val ended = JavaSessionTerminationPolicy()
            assertTrue(ended.requestCancellation(JvmCancellationReason.HOST_DIED, workerLive = true).accepted)
            assertTrue(ended.tryTerminal(outcome))
            assertFalse(ended.tryTerminal(JavaSessionTerminationPolicy.Outcome.SUCCESS))

            val recovered = JavaSessionTerminationPolicy()
            assertTrue(recovered.tryTerminal(JavaSessionTerminationPolicy.Outcome.SUCCESS))
        }
    }

    @Test
    fun terminalOrCancellationCanNeverDispatchWorkerArtifacts() {
        val cancelled = JavaSessionTerminationPolicy()
        assertTrue(cancelled.requestCancellation(JvmCancellationReason.REQUESTED, workerLive = false).accepted)
        assertFalse(cancelled.reserveWorkerDispatch())

        val terminal = JavaSessionTerminationPolicy()
        assertTrue(terminal.tryTerminal(JavaSessionTerminationPolicy.Outcome.SUCCESS))
        assertFalse(terminal.reserveWorkerDispatch())
    }

    @Test
    fun blockedDispatchDoesNotBlockCancellationAndDefersCancelUntilExecuteIsSubmitted() {
        val policy = JavaSessionTerminationPolicy()
        val dispatchEntered = CountDownLatch(1)
        val releaseDispatch = CountDownLatch(1)
        val cancellationReturned = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)
        try {
            assertTrue(policy.reserveWorkerDispatch())
            val dispatch = pool.submit<JavaSessionTerminationPolicy.DispatchCompletionPlan> {
                dispatchEntered.countDown()
                check(releaseDispatch.await(5, TimeUnit.SECONDS))
                policy.completeWorkerDispatch(submitted = true)
            }
            check(dispatchEntered.await(5, TimeUnit.SECONDS))
            val cancel = pool.submit {
                val plan = policy.requestCancellation(JvmCancellationReason.REQUESTED, workerLive = true)
                assertTrue(plan.waitForDispatchCompletion)
                assertFalse(plan.signalWorker)
                cancellationReturned.set(true)
            }
            cancel.get(5, TimeUnit.SECONDS)
            assertTrue(cancellationReturned.get())
            releaseDispatch.countDown()
            val completion = dispatch.get(5, TimeUnit.SECONDS)
            assertTrue(completion.signalWorker)
            assertTrue(completion.scheduleHardStop)
            assertFalse(completion.immediateHardStop)
            assertFalse(policy.canDispatchWorkerArtifacts())
        } finally {
            releaseDispatch.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun ordinaryPostDispatchCancellationKeepsCooperativeGraceBeforeHardStop() {
        val policy = JavaSessionTerminationPolicy()
        assertTrue(policy.reserveWorkerDispatch())
        policy.completeWorkerDispatch(submitted = true)

        val plan = policy.requestCancellation(JvmCancellationReason.TIMEOUT, workerLive = true)

        assertTrue(plan.signalWorker)
        assertTrue(plan.scheduleHardStop)
        assertFalse(policy.requiresImmediateWorkerHardStop())
        assertTrue(policy.cancellationGraceExpired())
        assertFalse(policy.cancellationGraceExpired())
    }

    @Test
    fun hostOrProviderDeathDuringReservationHardStopsOnlyAfterSubmission() {
        listOf(
            JavaSessionTerminationPolicy.Outcome.HOST_DIED,
            JavaSessionTerminationPolicy.Outcome.PROVIDER_DESTROYED,
        ).forEach { outcome ->
            val policy = JavaSessionTerminationPolicy()
            assertTrue(policy.reserveWorkerDispatch())
            val plan = policy.requestCancellation(JvmCancellationReason.HOST_DIED, workerLive = true)
            assertTrue(plan.waitForDispatchCompletion)
            assertTrue(policy.tryTerminal(outcome))
            assertFalse(policy.requiresImmediateWorkerHardStop())

            val completion = policy.completeWorkerDispatch(submitted = true)
            assertTrue(completion.immediateHardStop)
            assertFalse(completion.signalWorker)
            assertTrue(policy.requiresImmediateWorkerHardStop())
        }
    }
}
