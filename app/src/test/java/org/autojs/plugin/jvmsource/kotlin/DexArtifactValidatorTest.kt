package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DexArtifactValidatorTest {
    @Test
    fun rejectsSizeHashAndMalformedHeaderBeforeClassLoading() {
        val malformed = ByteArray(0x70)
        val sha = JvmSha256.digest(malformed)

        assertArtifactInvalid(malformed, malformed.size + 1L, sha)
        assertArtifactInvalid(malformed, malformed.size.toLong(), JvmSha256.digest(byteArrayOf(1)))
        assertArtifactInvalid(malformed, malformed.size.toLong(), sha)
    }

    private fun assertArtifactInvalid(bytes: ByteArray, size: Long, sha256: JvmSha256) {
        val failure = assertThrows(JavaProviderFailure::class.java) {
            DexArtifactValidator.validate(
                bytes = bytes,
                expectedSizeBytes = size,
                expectedSha256 = sha256,
                requestMinApi = 24,
                deviceApi = 24,
                expectedClassDescriptors = setOf("LMain;"),
            )
        }
        assertEquals(JvmSourceErrorCode.ARTIFACT_INVALID, failure.code)
        assertEquals(JvmSourceFailurePhase.WORKER_START, failure.phase)
    }
}
