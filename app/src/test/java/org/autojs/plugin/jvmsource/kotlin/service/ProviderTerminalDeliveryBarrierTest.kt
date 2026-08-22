package org.autojs.plugin.jvmsource.kotlin.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProviderTerminalDeliveryBarrierTest {
    @Test
    fun externalTerminalIsReleasedExactlyOnceOnlyAtRetirementBarrier() {
        val barrier = ProviderTerminalDeliveryBarrier<String>(JavaSessionTerminationPolicy())

        assertTrue(barrier.commitExternal(JavaSessionTerminationPolicy.Outcome.SUCCESS, "SUCCESS"))
        assertEquals(ProviderTerminalDeliveryBarrier.State.STAGED, barrier.snapshot())
        assertFalse(barrier.commitExternal(JavaSessionTerminationPolicy.Outcome.RUNTIME_ERROR, "RUNTIME_ERROR"))
        assertEquals("SUCCESS", barrier.retire().externalTerminal)
        assertFalse(barrier.retire().accepted)
        assertEquals(ProviderTerminalDeliveryBarrier.State.DELIVERED, barrier.snapshot())
    }

    @Test
    fun hostOrProviderDeathRetiresWithoutPublishingExternalTerminal() {
        val barrier = ProviderTerminalDeliveryBarrier<String>(JavaSessionTerminationPolicy())

        assertTrue(barrier.commitSilent(JavaSessionTerminationPolicy.Outcome.HOST_DIED))
        assertNull(barrier.retire().externalTerminal)
        assertEquals(ProviderTerminalDeliveryBarrier.State.RETIRED, barrier.snapshot())
        assertFalse(barrier.commitExternal(JavaSessionTerminationPolicy.Outcome.SUCCESS, "LATE"))
    }

    @Test
    fun cleanupCannotObserveTerminalBeforeItsExternalPayloadIsPublished() {
        val barrier = ProviderTerminalDeliveryBarrier<String>(JavaSessionTerminationPolicy())
        val transitionReached = CountDownLatch(1)
        val allowPublication = CountDownLatch(1)
        val retirementReturned = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val commit = pool.submit<Boolean> {
                barrier.commitExternal(JavaSessionTerminationPolicy.Outcome.SUCCESS, "SUCCESS") {
                    transitionReached.countDown()
                    check(allowPublication.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(transitionReached.await(5, TimeUnit.SECONDS))
            val retirement = pool.submit<ProviderTerminalDeliveryBarrier.Retirement<String>> {
                barrier.retire().also { retirementReturned.countDown() }
            }
            assertFalse(retirementReturned.await(50, TimeUnit.MILLISECONDS))
            allowPublication.countDown()
            assertTrue(commit.get(5, TimeUnit.SECONDS))
            assertEquals("SUCCESS", retirement.get(5, TimeUnit.SECONDS).externalTerminal)
        } finally {
            allowPublication.countDown()
            pool.shutdownNow()
        }
    }
}
