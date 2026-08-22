package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import org.autojs.plugin.jvmsource.kotlin.ValidatedDexArtifact
import org.autojs.plugin.jvmsource.kotlin.WorkerDexLoaderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkerDexLoadGateTest {
    @Test
    fun structureValidationStateDoesNotClaimArtLoadSuccess() {
        val validated = StructurallyValidatedWorkerDex(
            bytes = byteArrayOf(1),
            artifact = ValidatedDexArtifact(
                sizeBytes = 1L,
                sha256 = JvmSha256.digest(byteArrayOf(1)),
                version = "037",
                classDescriptors = setOf("LMain;"),
                requestMinApi = 24,
                deviceApi = 24,
                loaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
            ),
        )

        assertEquals(WorkerDexLoadStage.STRUCTURE_VALIDATED, validated.stage)
    }

    @Test
    fun artEntryLoadFailureHasASeparateStableCodeAndDoesNotInitializeParentMain() {
        System.clearProperty(INITIALIZATION_PROPERTY)
        val failure = assertThrows(JavaProviderFailure::class.java) {
            WorkerEntryFactory.loadFromArt(object : ClassLoader(javaClass.classLoader) {})
        }

        assertEquals(JvmSourceErrorCode.CLASS_LOADING_FAILED, failure.code)
        assertEquals(JvmSourceFailurePhase.WORKER_START, failure.phase)
        assertNull(System.getProperty(INITIALIZATION_PROPERTY))
    }

    @Test
    fun actualClassLoaderBranchMustMatchTheValidatedArtifactPolicy() {
        val artifact = ValidatedDexArtifact(
            sizeBytes = 1L,
            sha256 = JvmSha256.digest(byteArrayOf(1)),
            version = "037",
            classDescriptors = setOf("LMain;"),
            requestMinApi = 24,
            deviceApi = 24,
            loaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
        )

        assertThrows(IllegalArgumentException::class.java) {
            LoadedDex(
                classLoader = javaClass.classLoader!!,
                validatedArtifact = artifact,
                actualLoaderKind = WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            )
        }
    }

    private companion object {
        const val INITIALIZATION_PROPERTY = "autojs.jvm.source.test.main.initialized"
    }
}
