package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDexLoaderKind
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DexRuntimePolicyTest {
    @Test
    fun api24Through36SelectTheExplicitLoaderPolicy() {
        val expected = (24..36).associateWith { api ->
            if (api <= 25) {
                WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER
            } else {
                WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER
            }
        }

        assertEquals(expected, expected.mapValues { (api, _) -> DexRuntimePolicy.loaderKind(api) })
        assertEquals(WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER, DexRuntimePolicy.loaderKind(24))
        assertEquals(WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER, DexRuntimePolicy.loaderKind(25))
        assertEquals(WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER, DexRuntimePolicy.loaderKind(26))
        assertEquals(WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER, DexRuntimePolicy.loaderKind(28))
        assertEquals(WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER, DexRuntimePolicy.loaderKind(34))
        assertEquals(WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER, DexRuntimePolicy.loaderKind(36))
    }

    @Test
    fun api24Through36ExposeTheConservativeDexVersionMatrix() {
        val versionsByApi = (24..36).associateWith(DexRuntimePolicy::admittedVersions)

        assertEquals(setOf("035", "037"), versionsByApi.getValue(24))
        assertEquals(setOf("035", "037"), versionsByApi.getValue(25))
        assertEquals(setOf("035", "037", "038"), versionsByApi.getValue(26))
        assertEquals(setOf("035", "037", "038"), versionsByApi.getValue(27))
        assertEquals(setOf("035", "037", "038", "039"), versionsByApi.getValue(28))
        assertEquals(setOf("035", "037", "038", "039", "040"), versionsByApi.getValue(29))
        (30..36).forEach { api ->
            assertEquals(setOf("035", "037", "038", "039", "040"), versionsByApi.getValue(api))
        }
        versionsByApi.values.forEach { versions -> assertFalse("041" in versions) }
    }

    @Test
    fun providerPolicyReusesTheImmutableSharedRuntimeContractExactly() {
        (24..36).forEach { api ->
            assertSame(JvmDexRuntimeProfile.admittedVersions(api), DexRuntimePolicy.admittedVersions(api))
        }
        WorkerDexLoaderKind.entries.forEach { kind ->
            assertEquals(kind, WorkerDexLoaderKind.fromApi(kind.apiKind))
        }
        assertEquals(
            JvmDexLoaderKind.entries.toSet(),
            WorkerDexLoaderKind.entries.mapTo(linkedSetOf()) { it.apiKind },
        )
        assertEquals(
            JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
            DexRuntimePolicy.loaderKind(24).apiKind,
        )
        assertEquals(
            JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            DexRuntimePolicy.loaderKind(36).apiKind,
        )

        @Suppress("UNCHECKED_CAST")
        val shared = DexRuntimePolicy.admittedVersions(29) as MutableSet<String>
        assertThrows(UnsupportedOperationException::class.java) { shared.add("041") }
    }

    @Test
    fun deviceAndRequestMinApiAreIndependentAdmissionDimensions() {
        val deviceAllowsButRequestForbids = DexRuntimePolicy.evaluateVersion(
            deviceApi = 34,
            requestMinApi = 24,
            dexVersion = "040",
        )
        assertFalse(deviceAllowsButRequestForbids.accepted)
        assertEquals(
            DexVersionRejection.VERSION_VIOLATES_REQUEST_MIN_API,
            deviceAllowsButRequestForbids.rejection,
        )

        val requestExceedsDevice = DexRuntimePolicy.evaluateVersion(
            deviceApi = 25,
            requestMinApi = 26,
            dexVersion = "037",
        )
        assertFalse(requestExceedsDevice.accepted)
        assertEquals(DexVersionRejection.DEVICE_BELOW_REQUEST_MIN_API, requestExceedsDevice.rejection)

        val admitted = DexRuntimePolicy.evaluateVersion(
            deviceApi = 36,
            requestMinApi = 24,
            dexVersion = "037",
        )
        assertTrue(admitted.accepted)
        assertNull(admitted.rejection)
        assertEquals(WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER, admitted.loaderKind)
    }

    @Test
    fun api24D8RuntimeEvidenceIsNotEstablishedByThisPureAdmissionPolicy() {
        // This test covers format/loader policy only. ECJ/D8 execution on API 24 remains a
        // canonical-device R2 evidence item and must not be inferred from these assertions.
        assertEquals(setOf("035", "037"), DexRuntimePolicy.admittedVersions(24))
    }
}
