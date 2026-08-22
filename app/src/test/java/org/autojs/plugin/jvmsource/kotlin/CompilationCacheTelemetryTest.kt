package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Test

class CompilationCacheTelemetryTest {
    @Test
    fun countersSaturateWithoutWrappingNegative() {
        val counter = SaturatingCacheCounter(Long.MAX_VALUE - 1L)

        assertEquals(Long.MAX_VALUE, counter.incrementAndGet())
        assertEquals(Long.MAX_VALUE, counter.incrementAndGet())
        assertEquals(Long.MAX_VALUE, counter.get())
    }

    @Test
    fun missReasonsRemainBoundedProviderInternalObservations() {
        val telemetry = CompilationCacheTelemetry()
        CompilationCacheMissReason.entries.forEach(telemetry::recordMiss)

        val snapshot = telemetry.snapshot()
        assertEquals(CompilationCacheMissReason.entries.size.toLong(), snapshot.misses)
        CompilationCacheMissReason.entries.forEach { reason ->
            assertEquals(1L, snapshot.missesByReason.getValue(reason))
        }
    }
}
