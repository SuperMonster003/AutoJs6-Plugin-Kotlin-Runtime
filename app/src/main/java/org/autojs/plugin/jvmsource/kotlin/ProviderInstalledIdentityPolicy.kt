package org.autojs.plugin.jvmsource.kotlin

internal enum class ProviderInstalledIdentityDecision {
    MATCH,
    UNAVAILABLE,
    DRIFTED,
}

/** Exact, canonical installed-package identity comparison used before cache lookup and publish. */
internal object ProviderInstalledIdentityPolicy {
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    fun evaluate(
        expected: ProviderInstalledIdentity,
        current: ProviderInstalledIdentity?,
    ): ProviderInstalledIdentityDecision = when {
        current == null -> ProviderInstalledIdentityDecision.UNAVAILABLE
        !isCanonical(expected) || !isCanonical(current) -> ProviderInstalledIdentityDecision.DRIFTED
        current == expected -> ProviderInstalledIdentityDecision.MATCH
        else -> ProviderInstalledIdentityDecision.DRIFTED
    }

    private fun isCanonical(value: ProviderInstalledIdentity): Boolean =
        value.packageName.isNotBlank() &&
            value.componentName.isNotBlank() &&
            value.uid > 0 &&
            value.versionCode > 0L &&
            value.lastUpdateTime > 0L &&
            value.signerSha256.isNotEmpty() &&
            value.signerSha256 == value.signerSha256.distinct().sorted() &&
            value.signerSha256.all(SHA256_HEX::matches)
}
