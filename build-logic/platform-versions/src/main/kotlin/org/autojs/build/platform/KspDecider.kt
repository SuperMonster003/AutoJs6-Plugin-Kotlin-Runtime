package org.autojs.build.platform

import org.gradle.util.GradleVersion

/**
 * Decides the KSP plugin version and keeps AGP compatible with it.
 *
 * KSP releases are versioned after the Kotlin release they target, e.g. KSP
 * `2.2.21-2.0.4` targets Kotlin 2.2.21. Newer KSP releases also demand a minimum
 * AGP, so a project whose IDE only permits an older AGP would otherwise fail at
 * configuration time. This raises AGP to the lowest release that satisfies KSP
 * and the running Gradle both.
 *
 * zh-CN: 决定 KSP 插件版本, 并保证 AGP 与之兼容. KSP 的版本号跟随其目标 Kotlin 版本,
 * 如 KSP `2.2.21-2.0.4` 对应 Kotlin 2.2.21. 较新的 KSP 还要求最低 AGP 版本,
 * 若 IDE 只允许较旧的 AGP 就会在配置阶段失败; 此处会把 AGP 抬到同时满足 KSP
 * 与当前 Gradle 的最低发行版.
 */
class KspDecider(
    private val dataSource: DataSource,
    private val gradleVersion: String,
) {

    private val kspReleaseProps by lazy { dataSource.props("ksp-releases") }
    private val kspAgpCompatProps by lazy { dataSource.props("ksp-agp-compat") }
    private val agpGradleCompatProps by lazy { dataSource.props("agp-gradle-compat") }
    private val agpReleases by lazy { dataSource.list("agp-releases") }

    /**
     * Resolves the KSP version matching [kotlinVersion].
     *
     * Wildcard keys such as `2.3.Z` carry the full KSP version as their value, whereas
     * exact keys carry only the KSP suffix and are joined back onto the Kotlin version.
     */
    fun decideKspVersion(kotlinVersion: String): Decision {
        val matchedKey = VersionMapMatcher.findBestMatchingMapKey(kspReleaseProps, kotlinVersion)
            ?: return Decision(null, "")
        val matchedValue = kspReleaseProps[matchedKey] ?: return Decision(null, "")
        val hintSuffix = when (matchedKey != kotlinVersion) {
            true -> Identifier.NEAREST_LOWER_MATCHED_SUFFIX
            else -> ""
        }
        val version = when {
            matchedKey.contains(Regex("[xyz*?]", RegexOption.IGNORE_CASE)) -> matchedValue
            else -> "$matchedKey-$matchedValue"
        }
        return Decision(version, hintSuffix)
    }

    /** The lowest AGP version the given KSP release requires, or null when unconstrained. */
    fun minimumAgpVersionForKsp(kspVersion: String): String? = kspAgpCompatProps[kspVersion]

    /**
     * Raises [agpVersion] when the chosen KSP release needs a newer one.
     *
     * A user-pinned AGP is never rewritten silently: an incompatible pin throws, so the
     * mismatch surfaces at the point it was configured rather than deep inside a task.
     */
    fun refineAgpVersionForKsp(
        agpVersion: String,
        kspVersion: String?,
        isUserSpecified: Boolean,
    ): Decision {
        kspVersion ?: return Decision(agpVersion, "")
        val minimumAgpVersion = minimumAgpVersionForKsp(kspVersion) ?: return Decision(agpVersion, "")
        if (VersionComparator.compareVersionStrings(agpVersion, minimumAgpVersion) >= 0) {
            return Decision(agpVersion, "")
        }

        if (isUserSpecified) {
            throw IllegalStateException(
                "User specified AGP version $agpVersion is incompatible with KSP $kspVersion. " +
                        "KSP $kspVersion requires AGP $minimumAgpVersion or higher."
            )
        }

        val upgradedAgpVersion = getAgpReleasedVersionAtLeast(minimumAgpVersion)
            ?: throw IllegalStateException(
                "Failed to determine an AGP version compatible with KSP $kspVersion. " +
                        "KSP $kspVersion requires AGP $minimumAgpVersion or higher, " +
                        "but current Gradle $gradleVersion cannot use any known compatible stable AGP release."
            )

        return Decision(upgradedAgpVersion, Identifier.UPGRADED_SUFFIX)
    }

    /** Lowest stable AGP release that is at least [minimumAgpVersion] and usable by this Gradle. */
    fun getAgpReleasedVersionAtLeast(minimumAgpVersion: String): String? = agpReleases
        .asSequence()
        .filterNot { it.contains("-") }
        .filter { VersionComparator.compareVersionStrings(it, minimumAgpVersion) >= 0 }
        .sortedWith(VersionComparator::compareVersionStrings)
        .firstOrNull(::canUseAgpWithCurrentGradle)

    /** Tells whether the running Gradle can load the given AGP version. */
    fun canUseAgpWithCurrentGradle(agpVersion: String): Boolean {
        val matchedKey = VersionMapMatcher.findBestMatchingMapKey(agpGradleCompatProps, agpVersion)
            ?: return false
        val minimumGradleVersion = agpGradleCompatProps[matchedKey] ?: return false
        return GradleVersion.version(gradleVersion) >= GradleVersion.version(minimumGradleVersion)
    }

}
