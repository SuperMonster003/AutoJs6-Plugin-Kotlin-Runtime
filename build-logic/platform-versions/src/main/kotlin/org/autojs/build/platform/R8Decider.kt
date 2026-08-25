package org.autojs.build.platform

/**
 * Decides whether an external R8 is needed, and which version.
 *
 * AGP bundles its own R8, which is the right choice most of the time. Newer Kotlin
 * releases occasionally need an R8 that the current AGP does not yet bundle, and only
 * then is an explicit R8 put on the classpath and forced through the resolution
 * strategy.
 *
 * zh-CN: 决定是否需要外部 R8 及其版本. AGP 自带 R8, 多数情况下用自带的即可;
 * 只有当较新的 Kotlin 需要当前 AGP 尚未内置的 R8 时, 才显式引入外部 R8
 * 并通过 resolutionStrategy 强制生效.
 */
class R8Decider(private val dataSource: DataSource) {

    private val kotlinR8CompatProps by lazy { dataSource.props("kotlin-r8-compat") }

    /**
     * The minimum R8 version the given Kotlin release requires.
     *
     * The table is keyed by Kotlin's major-minor line, so "2.2.21" looks up "2.2".
     */
    fun minimumR8VersionFor(kotlinVersion: String): String? {
        val compatibilityLine = kotlinCompatibilityLine(kotlinVersion) ?: return null
        return kotlinR8CompatProps[compatibilityLine]
    }

    /**
     * Tells whether an external R8 is needed rather than the one AGP bundles.
     *
     * Compares by version line first: an AGP older than the required R8 line cannot
     * bundle a new enough R8, whereas a newer line always can.
     */
    fun shouldUseExternalR8(agpVersion: String, minimumR8Version: String): Boolean {
        val agpVersionLine = VersionComparator.toVersionParts(agpVersion).first.take(2)
        val r8VersionLine = VersionComparator.toVersionParts(minimumR8Version).first.take(2)
        return when (val comparison = VersionComparator.compareVersionParts(agpVersionLine, r8VersionLine)) {
            0 -> VersionComparator.compareVersionStrings(agpVersion, minimumR8Version) < 0
            else -> comparison < 0
        }
    }

    private fun kotlinCompatibilityLine(kotlinVersion: String): String? = kotlinVersion
        .substringBefore("-")
        .split('.')
        .take(2)
        .joinToString(".")
        .takeIf { it.isNotBlank() }

}
