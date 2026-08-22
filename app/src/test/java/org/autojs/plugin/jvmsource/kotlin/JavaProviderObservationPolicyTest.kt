package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaProviderObservationPolicyTest {
    @Test
    fun coldWarmClassificationNeverCallsAFreshWorkerWarm() {
        assertEquals(
            JavaProviderStartProfile.COLD,
            JavaProviderObservationPolicy.startProfile(false, false, JavaProviderObservedProcess.COMPILER),
        )
        assertEquals(
            JavaProviderStartProfile.WARM,
            JavaProviderObservationPolicy.startProfile(true, true, JavaProviderObservedProcess.COMPILER),
        )
        assertEquals(
            JavaProviderStartProfile.COLD,
            JavaProviderObservationPolicy.startProfile(true, true, JavaProviderObservedProcess.WORKER),
        )
    }

    @Test
    fun collectorRecordsEachBoundedPhaseOnceAndLeavesMissingMetricsExplicit() {
        val collector = JavaProviderObservationCollector(JavaProviderStartProfile.COLD)

        assertTrue(collector.recordDuration(JavaProviderObservedPhase.COMPILE, 12))
        assertTrue(collector.recordDuration(JavaProviderObservedPhase.D8, 34))
        assertTrue(collector.recordDuration(JavaProviderObservedPhase.LOAD, 5))
        assertTrue(collector.recordDuration(JavaProviderObservedPhase.RUN, 6))
        assertTrue(collector.recordDuration(JavaProviderObservedPhase.TERMINATION, 7))
        assertFalse(collector.recordDuration(JavaProviderObservedPhase.COMPILE, 99))
        assertFalse(collector.recordDuration(JavaProviderObservedPhase.D8, -1))

        val snapshot = collector.snapshot()
        assertEquals(12L, snapshot.compileDurationMillis)
        assertEquals(34L, snapshot.d8DurationMillis)
        assertEquals(5L, snapshot.loadDurationMillis)
        assertEquals(6L, snapshot.runDurationMillis)
        assertEquals(7L, snapshot.terminationDurationMillis)
        assertEquals(JavaProviderCacheOutcome.NOT_EVALUATED, snapshot.cacheOutcome)
        assertNull(snapshot.cacheMissReason)
        assertTrue(snapshot.resources.isEmpty())

        val empty = JavaProviderObservationCollector(JavaProviderStartProfile.WARM).snapshot()
        assertNull(empty.compileDurationMillis)
        assertNull(empty.runDurationMillis)
    }

    @Test
    fun resourceSamplesAreNumericBoundedAndStructurallyNonSensitive() {
        val collector = JavaProviderObservationCollector(JavaProviderStartProfile.WARM)
        val admitted = JavaProviderResourceObservation(
            process = JavaProviderObservedProcess.COMPILER,
            phase = JavaProviderObservedPhase.D8,
            rssBytes = 96L * 1024L * 1024L,
            openFileDescriptorCount = 47,
            temporaryStorageBytes = 8L * 1024L * 1024L,
            outputBytes = JvmSourceContract.MAX_STDOUT_BYTES,
        )
        assertTrue(collector.recordResource(admitted))
        assertFalse(collector.recordResource(admitted.copy(rssBytes = -1)))
        assertFalse(collector.recordResource(admitted.copy(openFileDescriptorCount = Int.MAX_VALUE)))
        assertFalse(collector.recordResource(admitted.copy(temporaryStorageBytes = Long.MAX_VALUE)))
        assertFalse(collector.recordResource(admitted.copy(outputBytes = Long.MAX_VALUE)))
        assertFalse(
            collector.recordResource(
                admitted.copy(
                    rssBytes = null,
                    openFileDescriptorCount = null,
                    temporaryStorageBytes = null,
                    outputBytes = null,
                ),
            ),
        )

        val snapshot = collector.snapshot()
        assertEquals(listOf(admitted), snapshot.resources)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.resources as MutableList<JavaProviderResourceObservation>).add(admitted)
        }
        val fieldNames = JavaProviderResourceObservation::class.java.declaredFields.map { it.name.lowercase() }
        listOf("path", "signer", "binder", "uid", "pid", "component", "package").forEach { forbidden ->
            assertTrue(fieldNames.none { forbidden in it })
        }
    }

    @Test
    fun cacheObservationUsesOnlyBoundedEnumsAndNoKeyOrPath() {
        val hit = JavaProviderObservationCollector(JavaProviderStartProfile.WARM)
        assertTrue(hit.recordCache(JavaProviderCacheOutcome.HIT))
        assertFalse(hit.recordCache(JavaProviderCacheOutcome.MISS, JavaProviderCacheMissReason.NOT_FOUND))
        assertEquals(JavaProviderCacheOutcome.HIT, hit.snapshot().cacheOutcome)

        val miss = JavaProviderObservationCollector(JavaProviderStartProfile.COLD)
        assertFalse(miss.recordCache(JavaProviderCacheOutcome.MISS))
        assertTrue(
            miss.recordCache(
                JavaProviderCacheOutcome.MISS,
                JavaProviderCacheMissReason.MATERIALIZATION_FAILED,
            ),
        )
        val snapshot = miss.snapshot()
        assertEquals(JavaProviderCacheOutcome.MISS, snapshot.cacheOutcome)
        assertEquals(JavaProviderCacheMissReason.MATERIALIZATION_FAILED, snapshot.cacheMissReason)
        val disabled = JavaProviderObservationCollector(JavaProviderStartProfile.COLD)
        assertTrue(disabled.recordCache(JavaProviderCacheOutcome.MISS, JavaProviderCacheMissReason.CACHE_DISABLED))
        assertEquals(JavaProviderCacheMissReason.CACHE_DISABLED, disabled.snapshot().cacheMissReason)
        val identityDrift = JavaProviderObservationCollector(JavaProviderStartProfile.COLD)
        assertTrue(
            identityDrift.recordCache(
                JavaProviderCacheOutcome.MISS,
                JavaProviderCacheMissReason.PROVIDER_IDENTITY_DRIFTED,
            ),
        )
        assertEquals(
            JavaProviderCacheMissReason.PROVIDER_IDENTITY_DRIFTED,
            JavaProviderObservationCodec.decode(
                JavaProviderObservationCodec.encode(identityDrift.snapshot()),
            ).cacheMissReason,
        )
        val fieldNames = JavaProviderObservation::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fieldNames.none { "key" in it || "path" in it })
    }

    @Test
    fun processRegistryRetainsOnlyTheLastBoundedSnapshotAndWorkerIsAlwaysCold() {
        JavaProviderObservationRegistry.resetForTest()
        assertEquals(
            JavaProviderStartProfile.COLD,
            JavaProviderObservationRegistry.compilerStartProfile(environmentWasAlreadyInitialized = true),
        )
        val first = JavaProviderObservationCollector(JavaProviderStartProfile.COLD).snapshot()
        JavaProviderObservationRegistry.publishCompiler(first)
        assertEquals(
            JavaProviderStartProfile.WARM,
            JavaProviderObservationRegistry.compilerStartProfile(environmentWasAlreadyInitialized = true),
        )
        assertEquals(first, JavaProviderObservationRegistry.compilerSnapshot())
        assertEquals(JavaProviderStartProfile.COLD, JavaProviderObservationRegistry.workerStartProfile())
    }

    @Test
    fun internalCodecRoundTripsOnlyTheBoundedModelAndRejectsMalformedInput() {
        val collector = JavaProviderObservationCollector(JavaProviderStartProfile.COLD)
        assertTrue(collector.recordDuration(JavaProviderObservedPhase.LOAD, 8))
        assertTrue(collector.recordDuration(JavaProviderObservedPhase.RUN, 13))
        assertTrue(
            collector.recordResource(
                JavaProviderResourceObservation(
                    JavaProviderObservedProcess.WORKER,
                    JavaProviderObservedPhase.RUN,
                    rssBytes = 1234,
                    openFileDescriptorCount = 8,
                    temporaryStorageBytes = 50,
                    outputBytes = 60,
                ),
            ),
        )
        val expected = collector.snapshot()
        val encoded = JavaProviderObservationCodec.encode(expected)

        assertTrue(encoded.size <= JavaProviderObservationCodec.MAX_ENCODED_BYTES)
        assertEquals(expected, JavaProviderObservationCodec.decode(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            JavaProviderObservationCodec.decode(encoded + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JavaProviderObservationCodec.decode(ByteArray(JavaProviderObservationCodec.MAX_ENCODED_BYTES + 1))
        }
    }

    @Test
    fun sampleCountIsStrictlyBounded() {
        val collector = JavaProviderObservationCollector(JavaProviderStartProfile.COLD)
        repeat(JavaProviderObservationPolicy.MAX_RESOURCE_SAMPLES) { index ->
            assertTrue(
                collector.recordResource(
                    JavaProviderResourceObservation(
                        JavaProviderObservedProcess.WORKER,
                        JavaProviderObservedPhase.RUN,
                        rssBytes = index.toLong(),
                        openFileDescriptorCount = null,
                        temporaryStorageBytes = null,
                        outputBytes = null,
                    ),
                ),
            )
        }
        assertFalse(
            collector.recordResource(
                JavaProviderResourceObservation(
                    JavaProviderObservedProcess.WORKER,
                    JavaProviderObservedPhase.TERMINATION,
                    rssBytes = 1,
                    openFileDescriptorCount = 1,
                    temporaryStorageBytes = 1,
                    outputBytes = 1,
                ),
            ),
        )
    }
}
