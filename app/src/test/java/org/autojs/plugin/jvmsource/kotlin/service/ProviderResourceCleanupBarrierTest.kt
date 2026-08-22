package org.autojs.plugin.jvmsource.kotlin.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProviderResourceCleanupBarrierTest {
    @Test
    fun workerDeathBetweenCleanupClaimAndResourceCloseCannotFinalizeEarly() {
        val barrier = ProviderResourceCleanupBarrier()
        val cleanupClaimed = CountDownLatch(1)
        val allowResourcePublication = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val cleanup = pool.submit<Boolean> {
                assertTrue(barrier.beginCleanup())
                cleanupClaimed.countDown()
                check(allowResourcePublication.await(5, TimeUnit.SECONDS))
                barrier.resourcesClosed(waitForWorkerDeath = true)
            }
            assertTrue(cleanupClaimed.await(5, TimeUnit.SECONDS))
            assertFalse(barrier.observeWorkerDeath())
            assertEquals(ProviderResourceCleanupBarrier.State.CLOSING, barrier.snapshot())
            allowResourcePublication.countDown()
            assertTrue(cleanup.get(5, TimeUnit.SECONDS))
            assertEquals(ProviderResourceCleanupBarrier.State.RESOURCES_READY, barrier.snapshot())
        } finally {
            allowResourcePublication.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun liveWorkerWaitsAfterResourcesCloseAndDeathThenFinalizes() {
        val barrier = ProviderResourceCleanupBarrier()

        assertTrue(barrier.beginCleanup())
        assertFalse(barrier.resourcesClosed(waitForWorkerDeath = true))
        assertTrue(barrier.observeWorkerDeath())
    }
}
