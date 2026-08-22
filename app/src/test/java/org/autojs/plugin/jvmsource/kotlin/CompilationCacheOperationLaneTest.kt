package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CompilationCacheOperationLaneTest {
    @Test
    fun completedAndFailedOperationsReleaseTheSingleSlot() {
        CompilationCacheOperationLane(timeoutMillis = 1_000L).use { lane ->
            assertEquals(7, (lane.execute { 7 } as CompilationCacheOperationResult.Success).value)
            val failed = lane.execute<Int> { error("expected") }
            assertTrue(failed is CompilationCacheOperationResult.Unavailable)
            assertEquals(
                CompilationCacheOperationFailure.FAILED,
                (failed as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertEquals(9, (lane.execute { 9 } as CompilationCacheOperationResult.Success).value)
        }
    }

    @Test
    fun busyLaneRejectsWithoutQueueingASecondOperation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicReference<CompilationCacheOperationResult<Int>>()
        CompilationCacheOperationLane(timeoutMillis = 2_000L).use { lane ->
            val caller = Thread {
                first.set(
                    lane.execute {
                        entered.countDown()
                        check(release.await(1, TimeUnit.SECONDS))
                        1
                    },
                )
            }
            caller.start()
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val started = System.nanoTime()
            val rejected = lane.execute { 2 }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(
                CompilationCacheOperationFailure.REJECTED,
                (rejected as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertTrue("busy rejection took $elapsedMillis ms", elapsedMillis < 500L)
            release.countDown()
            caller.join(1_000L)
            assertEquals(1, (first.get() as CompilationCacheOperationResult.Success).value)
        }
    }

    @Test
    fun timeoutPoisonsTheOnlyWorkerAndLaterRequestsFailFast() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CompilationCacheOperationLane(timeoutMillis = 50L).use { lane ->
            val worker = lane.workerForTest()
            val timedOut = lane.execute {
                entered.countDown()
                while (true) {
                    try {
                        if (release.await(10, TimeUnit.MILLISECONDS)) break
                    } catch (_: InterruptedException) {
                        // Model platform I/O that does not cooperate with interruption.
                    }
                }
                1
            }
            assertTrue(entered.count == 0L)
            assertEquals(
                CompilationCacheOperationFailure.TIMED_OUT,
                (timedOut as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertTrue(lane.isPoisonedForTest())
            assertTrue(!lane.hasPendingForTest())
            val started = System.nanoTime()
            val afterTimeout = lane.execute { 2 }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(
                CompilationCacheOperationFailure.POISONED,
                (afterTimeout as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertTrue("poisoned rejection took $elapsedMillis ms", elapsedMillis < 500L)
            assertSame(worker, lane.workerForTest())
            release.countDown()
        }
    }

    @Test
    fun closeBeforeAdmissionReturnsClosedWithoutRunningTheOperation() {
        val lane = CompilationCacheOperationLane(timeoutMillis = 5_000L)
        lane.close()
        var invoked = false

        val result = lane.execute { invoked = true }

        assertEquals(
            CompilationCacheOperationFailure.CLOSED,
            (result as CompilationCacheOperationResult.Unavailable).failure,
        )
        assertTrue(!invoked)
    }

    @Test
    fun closeWhileActiveCancelsTheTrackedTaskAndUnblocksTheCaller() {
        val entered = CountDownLatch(1)
        val result = AtomicReference<CompilationCacheOperationResult<Int>>()
        val lane = CompilationCacheOperationLane(timeoutMillis = 5_000L)
        val caller = Thread {
            result.set(
                lane.execute {
                    entered.countDown()
                    CountDownLatch(1).await()
                    1
                },
            )
        }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        lane.close()
        assertTrue(!lane.hasPendingForTest())
        caller.join(1_000L)

        assertTrue(!caller.isAlive)
        assertEquals(
            CompilationCacheOperationFailure.CLOSED,
            (result.get() as CompilationCacheOperationResult.Unavailable).failure,
        )
    }
}
