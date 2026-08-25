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

    @Test
    fun fiveColdWarmPairsProduceTheBenchmarkCounterDelta() {
        val telemetry = CompilationCacheTelemetry()

        repeat(5) {
            telemetry.recordMiss(CompilationCacheMissReason.NOT_FOUND)
            telemetry.recordPublication()
            telemetry.recordHit()
        }

        val snapshot = telemetry.snapshot()
        assertEquals(5L, snapshot.hits)
        assertEquals(5L, snapshot.misses)
        assertEquals(5L, snapshot.missesByReason.getValue(CompilationCacheMissReason.NOT_FOUND))
        assertEquals(5L, snapshot.publications)
        assertEquals(0L, snapshot.publicationFailures)
    }

    @Test
    fun releaseStressMixProducesTheDocumentedCounterDelta() {
        val telemetry = CompilationCacheTelemetry()
        telemetry.recordMiss(CompilationCacheMissReason.NOT_FOUND)
        telemetry.recordPublication()
        repeat(44) { telemetry.recordHit() }
        repeat(5) { telemetry.recordMiss(CompilationCacheMissReason.NOT_FOUND) }

        val snapshot = telemetry.snapshot()
        assertEquals(44L, snapshot.hits)
        assertEquals(6L, snapshot.misses)
        assertEquals(6L, snapshot.missesByReason.getValue(CompilationCacheMissReason.NOT_FOUND))
        assertEquals(1L, snapshot.publications)
        assertEquals(0L, snapshot.publicationFailures)
    }

    @Test
    fun debugStressMixRecordsEveryRequestAsCacheDisabled() {
        val telemetry = CompilationCacheTelemetry()
        repeat(50) { telemetry.recordMiss(CompilationCacheMissReason.CACHE_DISABLED) }

        val snapshot = telemetry.snapshot()
        assertEquals(0L, snapshot.hits)
        assertEquals(50L, snapshot.misses)
        assertEquals(50L, snapshot.missesByReason.getValue(CompilationCacheMissReason.CACHE_DISABLED))
        assertEquals(0L, snapshot.publications)
        assertEquals(0L, snapshot.publicationFailures)
    }
}
