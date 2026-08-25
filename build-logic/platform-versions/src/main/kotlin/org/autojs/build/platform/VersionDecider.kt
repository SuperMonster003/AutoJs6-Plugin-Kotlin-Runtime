package org.autojs.build.platform

import org.autojs.build.platform.VersionComparator.sortByVersionName
import org.gradle.util.GradleVersion

/** A decided version plus the hint suffix explaining how it was reached. */
data class Decision(val version: String?, val hintSuffix: String)

/**
 * Decides which AGP and Kotlin Gradle plugin versions to put on the buildscript classpath.
 *
 * The AGP decision runs in three steps:
 * 1. nearest-lower match of the platform version against the platform's AGP map;
 * 2. a staleness check, so an IDE newer than the whole map falls back to auto selection
 *    instead of being downgraded to the newest known entry;
 * 3. capping against the AGP-to-Gradle compatibility table, so the result never exceeds
 *    what the running Gradle can load.
 *
 * zh-CN: 决定放到 buildscript classpath 上的 AGP 与 Kotlin Gradle 插件版本. AGP 决策分三步:
 * 1. 用平台版本对该平台的 AGP 映射表做就近向下匹配;
 * 2. 滞后判定: 若 IDE 比整张映射表都新, 回退到 auto 选择, 而不是降级到映射表中最新的已知条目;
 * 3. 按 AGP-Gradle 兼容表封顶, 保证结果不超出当前 Gradle 能加载的范围.
 */
class VersionDecider(
    private val platform: Platform,
    private val dataSource: DataSource,
    private val gradleVersion: String,
) {

    private val agpReleases by lazy { dataSource.list("agp-releases") }
    private val agpGradleCompatProps by lazy { dataSource.props("agp-gradle-compat") }
    private val gradleKotlinCompatProps by lazy { dataSource.props("gradle-kotlin-compat") }
    private val javaGradleCompatProps by lazy { dataSource.props("java-gradle-compat") }

    /** Decides the AGP version for the current platform and Gradle version. */
    fun decideAgpVersion(): Decision {
        val map = platform.agpVersionMap
        val matchedKey = VersionMapMatcher.findBestMatchingMapKey(map, platform.version)
        val matchedValue = matchedKey?.let { map[it] }
        var hintSuffix = when (matchedKey != null && matchedKey != platform.version) {
            true -> Identifier.NEAREST_LOWER_MATCHED_SUFFIX
            else -> ""
        }

        val maxSupportedAgpVersion = maxSupportedAgpVersion()
            ?: return Decision(matchedValue, hintSuffix)

        if (matchedValue != null && VersionMapMatcher.isPlatformNewerThanVersionMap(map, platform.version)) {
            // The platform is newer than every entry of the manually maintained agpVersionMap,
            // i.e. the map is stale. A newer IDE supports at least what its predecessors did,
            // so instead of silently downgrading to the newest known entry (which may be too
            // old to build this project), fall back to auto selection: the newest released AGP
            // compatible with the current Gradle version.
            // zh-CN: 当前平台版本比 agpVersionMap 全部条目都新, 说明手动维护的映射表已滞后.
            // 更新的 IDE 至少支持其前代所支持的 AGP, 因此不再静默降级到映射表中最新的已知条目
            // (可能旧到无法构建本项目), 而是回退到 auto 选择: 与当前 Gradle 兼容的最新 AGP 正式版.
            //
            // The fallback is capped one minor line above the newest known entry. Auto
            // selection alone is bounded only by Gradle, which on a new enough wrapper
            // overshoots what the IDE accepts: an IDE stating "Latest supported version is
            // AGP 9.1.0" refuses the 9.2.1 that Gradle 9.6.1 would otherwise allow. One line
            // of headroom covers the usual case of an IDE a little ahead of the map, without
            // pretending to know how far ahead it really is.
            // zh-CN: 回退结果被限制在最新已知条目之上一个小版本线内. 单靠 auto 选择只受 Gradle 约束,
            // 在较新的 wrapper 上会超出 IDE 的接受范围: 声明 "Latest supported version is AGP 9.1.0"
            // 的 IDE 会拒绝 Gradle 9.6.1 本可选出的 9.2.1. 放宽一个版本线足以覆盖
            // "IDE 略新于映射表" 这一常见情形, 又不假装知道它究竟新多少.
            val ceiling = agpFallbackCeiling(map)
            val capped = when {
                ceiling != null && VersionComparator.compareVersionStrings(maxSupportedAgpVersion, ceiling) > 0 -> ceiling
                else -> maxSupportedAgpVersion
            }
            // The [auto-specified] suffix on the version line is report enough. A console
            // note spelling the same thing out ran longer than the summary it explained.
            // zh-CN: 版本行上的 [auto-specified] 后缀已足以说明来由. 再用一条控制台注记复述,
            // 反而比它所解释的摘要还长.
            return Decision(capped, Identifier.AUTO_SPECIFIED_SUFFIX)
        }

        matchedValue ?: return Decision(maxSupportedAgpVersion, hintSuffix + Identifier.AUTO_SPECIFIED_SUFFIX)

        if (!matchedValue.contains("\\d+\\.\\d+\\.\\d+".toRegex())) {
            hintSuffix += Identifier.AUTO_SPECIFIED_SUFFIX
            return Decision(getAgpReleasedVersion(matchedValue) ?: "$matchedValue.0", hintSuffix)
        }

        if (VersionComparator.compareVersionStrings(matchedValue, maxSupportedAgpVersion) > 0) {
            hintSuffix += Identifier.DOWNGRADED_SUFFIX
            return Decision(maxSupportedAgpVersion, hintSuffix)
        }

        return Decision(matchedValue, hintSuffix)
    }

    /**
     * Decides the Kotlin Gradle plugin version.
     *
     * Kotlin tracks Gradle rather than the IDE, so this always lands on the newest
     * Kotlin the running Gradle supports.
     */
    fun decideKotlinVersion(): Decision {
        val supported = gradleKotlinCompatProps.filter { (gradleMin, _) ->
            gradleVersion.toGradleVersion() >= gradleMin.toGradleVersion()
        }
        val kotlinMin = supported.maxByOrNull { it.key.toGradleVersion() }?.value

        val map = supported.toSortedMap(VersionComparator::compareVersionStringsDesc)
        val matchedKey = VersionMapMatcher.findBestMatchingMapKey(map, platform.version)
        val matchedValue = matchedKey?.let { map[it] }
        var hintSuffix = when (matchedKey != null && matchedKey != platform.version) {
            true -> Identifier.NEAREST_LOWER_MATCHED_SUFFIX
            else -> ""
        }

        return when {
            kotlinMin == null -> Decision(matchedValue, hintSuffix)
            matchedValue == null -> {
                hintSuffix += Identifier.AUTO_SPECIFIED_SUFFIX
                Decision(kotlinMin, hintSuffix)
            }
            matchedValue.toGradleVersion() < kotlinMin.toGradleVersion() -> {
                hintSuffix += Identifier.UPGRADED_SUFFIX
                Decision(kotlinMin, hintSuffix)
            }
            matchedValue.toGradleVersion() > kotlinMin.toGradleVersion() -> {
                hintSuffix += Identifier.DOWNGRADED_SUFFIX
                Decision(kotlinMin, hintSuffix)
            }
            else -> Decision(matchedValue, hintSuffix)
        }
    }

    /** Lowest version the AGP map may fall back to, used when nothing else matches. */
    fun agpFallbackVersion(): String? =
        platform.agpVersionMap.values.minWithOrNull(VersionComparator::compareVersionStrings)

    /**
     * Highest AGP the stale-map fallback may reach, or null when the map says nothing.
     *
     * How far past the newest known entry the fallback may go depends on how far past
     * the newest known IDE the running one is. A patch-level IDE update, 2026.2 to
     * 2026.2.1, ships no new AGP support, so the ceiling stays where the map put it.
     * Only a minor-level update, 2026.2 to 2026.3, earns one AGP line of headroom.
     *
     * Treating every newer IDE as worth a line was what let 2026.2.1 reach AGP 9.2.1,
     * which the IDE then rejected with "Latest supported version is AGP 9.1.0".
     *
     * The ceiling never drops below the lowest AGP this Gradle can load, since a map so
     * far behind that its own line is unusable says nothing useful about the IDE.
     *
     * zh-CN: 回退可越过最新已知条目多远, 取决于当前 IDE 比最新已知 IDE 新多少.
     * 补丁级更新 (2026.2 -> 2026.2.1) 不带来新的 AGP 支持, 上界保持映射表给出的值;
     * 只有次版本级更新 (2026.2 -> 2026.3) 才放宽一个 AGP 版本线.
     * 此前对任何更新的 IDE 都放宽一线, 正是 2026.2.1 取到 AGP 9.2.1 的原因,
     * 而 IDE 会以 "Latest supported version is AGP 9.1.0" 拒绝它.
     * 上界不会低于当前 Gradle 能加载的最低 AGP: 若映射表滞后到连自身版本线都无法使用,
     * 它对当前 IDE 的能力就不再有参考价值.
     */
    fun agpFallbackCeiling(map: Map<String, String>): String? {
        val newestKnown = map.values.maxWithOrNull(VersionComparator::compareVersionStrings) ?: return null
        val parts = runCatching { VersionComparator.toVersionParts(newestKnown).first }.getOrNull() ?: return null
        val major = parts.getOrNull(0) ?: return null
        val minor = parts.getOrNull(1) ?: 0

        val headroom = if (isPlatformMinorNewerThanVersionMap(map)) 1 else 0
        val ceiling = getAgpReleasedVersion("$major.${minor + headroom}")
            ?: getAgpReleasedVersion("$major.$minor")
            ?: newestKnown

        val lowestUsable = agpGradleCompatProps.entries
            .filter { gradleVersion.toGradleVersion() >= it.value.toGradleVersion() }
            .minWithOrNull { a, b -> VersionComparator.compareVersionStrings(a.key, b.key) }
            ?.let { getAgpReleasedVersion(it.key) }
            ?: return ceiling

        return when {
            VersionComparator.compareVersionStrings(ceiling, lowestUsable) < 0 -> lowestUsable
            else -> ceiling
        }
    }

    /**
     * Tells whether the platform is a minor release ahead of the newest map entry,
     * rather than merely a patch ahead of it.
     */
    private fun isPlatformMinorNewerThanVersionMap(map: Map<String, String>): Boolean {
        val newestKey = map.keys.maxWithOrNull(VersionComparator::compareVersionStrings) ?: return false
        val known = runCatching { VersionComparator.toVersionParts(newestKey).first }.getOrNull() ?: return false
        val current = runCatching { VersionComparator.toVersionParts(platform.version).first }.getOrNull() ?: return false
        // Compare only the leading two segments, which is where an IDE states its line.
        return VersionComparator.compareVersionParts(current.take(2), known.take(2)) > 0
    }

    /**
     * The newest released AGP that the running Gradle version can load, or null when
     * the running Gradle is too old for any of them.
     *
     * Returning null matters now that support starts at AGP 9.0: handing a Gradle 8
     * build the lowest known AGP would put a version on the classpath that it cannot
     * load, failing later and less legibly than saying so here.
     *
     * zh-CN: 当前 Gradle 能加载的最新 AGP 正式版; 若当前 Gradle 旧于全部条目则返回 null.
     * 支持范围自 AGP 9.0 起后这一点尤为重要: 给 Gradle 8 构建返回已知最低的 AGP,
     * 只会把一个它加载不了的版本放到 classpath 上, 失败得更晚也更难懂.
     */
    fun maxSupportedAgpVersion(): String? {
        val currentGradleVersion = gradleVersion.toGradleVersion()
        return agpGradleCompatProps.entries
            .sortedByDescending { it.value.toGradleVersion() }
            .firstOrNull { currentGradleVersion >= it.value.toGradleVersion() }
            ?.let { getAgpReleasedVersion(it.key) }
    }

    /** Newest stable AGP release carrying the given prefix, e.g. "9.0" resolves to "9.0.1". */
    fun getAgpReleasedVersion(referenceAgpVersion: String): String? = with(VersionComparator) {
        agpReleases.sortByVersionName(isDescend = true)
            .find { it.startsWith(referenceAgpVersion) && !it.contains("-") }
    }

    /**
     * The highest Java version the given Gradle version fully supports.
     *
     * Exposed so downstream build logic can coerce toolchains rather than fail late.
     */
    fun getMaxSupportedJavaVersion(gradleVersion: String): Int {

        fun parseVersion(version: String) = version.split(Regex("[.-]")).map { it.toIntOrNull() ?: 0 }

        val inputGradleVersionInts = parseVersion(gradleVersion)
        val sortedJavaGradleCompatibility = javaGradleCompatProps.entries.map {
            it.key.toInt() to it.value
        }.sortedBy { it.first }

        var maxJavaVersion: Int = sortedJavaGradleCompatibility.first().first

        for ((presetJavaVersion, presetGradleVersion) in sortedJavaGradleCompatibility) {
            val presetGradleVersionInts: List<Int> = parseVersion(presetGradleVersion)

            for (i in presetGradleVersionInts.indices) {
                when {
                    i > inputGradleVersionInts.lastIndex -> break
                    inputGradleVersionInts[i] > presetGradleVersionInts[i] -> {
                        maxJavaVersion = presetJavaVersion
                        break
                    }
                    inputGradleVersionInts[i] < presetGradleVersionInts[i] -> break
                    i == presetGradleVersionInts.lastIndex -> maxJavaVersion = presetJavaVersion
                }
            }
        }

        return maxJavaVersion
    }

    private fun String.toGradleVersion() = GradleVersion.version(this)

}
