package org.autojs.plugin.jvmsource.kotlin

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

/** Provider-process-only counters; Protocol V1 intentionally exports no cache-specific fields. */
internal class CompilationCacheTelemetry {
    private val hits = SaturatingCacheCounter()
    private val misses = SaturatingCacheCounter()
    private val missesByReason = ConcurrentHashMap<CompilationCacheMissReason, SaturatingCacheCounter>().apply {
        CompilationCacheMissReason.entries.forEach { put(it, SaturatingCacheCounter()) }
    }
    private val publications = SaturatingCacheCounter()
    private val publicationFailures = SaturatingCacheCounter()

    fun recordHit() = hits.incrementAndGet()
    fun recordMiss(reason: CompilationCacheMissReason) {
        misses.incrementAndGet()
        missesByReason.getValue(reason).incrementAndGet()
    }
    fun recordPublication() = publications.incrementAndGet()
    fun recordPublicationFailure() = publicationFailures.incrementAndGet()

    fun snapshot() = Snapshot(
        hits = hits.get(),
        misses = misses.get(),
        missesByReason = CompilationCacheMissReason.entries.associateWith { missesByReason.getValue(it).get() },
        publications = publications.get(),
        publicationFailures = publicationFailures.get(),
    )

    data class Snapshot(
        val hits: Long,
        val misses: Long,
        val missesByReason: Map<CompilationCacheMissReason, Long>,
        val publications: Long,
        val publicationFailures: Long,
    )
}

internal class SaturatingCacheCounter(initialValue: Long = 0L) {
    private val value = AtomicLong(initialValue)

    init {
        require(initialValue >= 0L)
    }

    fun incrementAndGet(): Long {
        while (true) {
            val current = value.get()
            if (current == Long.MAX_VALUE) return current
            if (value.compareAndSet(current, current + 1L)) return current + 1L
        }
    }

    fun get(): Long = value.get()
}
