package org.autojs.plugin.jvmsource.kotlin.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WorkerCompletionSignalTest {
    @Test
    fun deliversTheIndependentFailsafeActionOnlyOnce() {
        val deliveries = AtomicInteger(0)
        val signal = WorkerCompletionSignal { deliveries.incrementAndGet() }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            repeat(16) {
                pool.execute {
                    start.await()
                    signal.notifyOnce()
                }
            }
            start.countDown()
            pool.shutdown()
            check(pool.awaitTermination(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, deliveries.get())
    }
}
