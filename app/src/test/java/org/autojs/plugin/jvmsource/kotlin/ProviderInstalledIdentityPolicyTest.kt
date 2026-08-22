package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderInstalledIdentityPolicyTest {
    @Test
    fun admitsOnlyAnExactCanonicalLiveIdentity() {
        val expected = identity()
        assertEquals(
            ProviderInstalledIdentityDecision.MATCH,
            ProviderInstalledIdentityPolicy.evaluate(expected, expected.copy()),
        )
        assertEquals(
            ProviderInstalledIdentityDecision.UNAVAILABLE,
            ProviderInstalledIdentityPolicy.evaluate(expected, current = null),
        )
    }

    @Test
    fun everyInstalledIdentityDriftDimensionIsFailClosed() {
        val expected = identity()
        val drifted = listOf(
            expected.copy(packageName = "other.package"),
            expected.copy(componentName = "pkg/.OtherService"),
            expected.copy(uid = expected.uid + 1),
            expected.copy(signerSha256 = listOf("b".repeat(64))),
            expected.copy(versionCode = expected.versionCode + 1L),
            expected.copy(versionName = "other"),
            expected.copy(lastUpdateTime = expected.lastUpdateTime + 1L),
            expected.copy(signerSha256 = expected.signerSha256 + expected.signerSha256),
            expected.copy(signerSha256 = listOf("A".repeat(64))),
        )
        drifted.forEach { current ->
            assertEquals(
                ProviderInstalledIdentityDecision.DRIFTED,
                ProviderInstalledIdentityPolicy.evaluate(expected, current),
            )
        }
    }

    private fun identity() = ProviderInstalledIdentity(
        packageName = "org.autojs.plugin.jvmsource.kotlin",
        componentName = "org.autojs.plugin.jvmsource.kotlin/.service.JavaSourceCompilerService",
        uid = 12_345,
        signerSha256 = listOf("a".repeat(64)),
        versionCode = 1L,
        versionName = "0.1.0-r3",
        lastUpdateTime = 123L,
    )
}
