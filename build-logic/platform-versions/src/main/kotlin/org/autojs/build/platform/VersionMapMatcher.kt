package org.autojs.build.platform

import org.autojs.build.platform.VersionComparator.compareVersionParts
import org.autojs.build.platform.VersionComparator.compareVersionStrings
import org.autojs.build.platform.VersionComparator.compareVersionSuffix
import org.autojs.build.platform.VersionComparator.toVersionParts

/**
 * Nearest-lower matching against a manually maintained version map, plus the
 * staleness check that guards against silent downgrades.
 *
 * zh-CN: 针对手动维护的版本映射表进行 "就近向下匹配", 并提供 "映射表滞后" 判定,
 * 用于避免 IDE 版本新于映射表时被静默降级.
 */
object VersionMapMatcher {

    /**
     * Finds the newest map key that is not newer than [platformVersion].
     *
     * Returns null when every key is newer than the platform, which happens on an
     * IDE older than anything the map knows about.
     */
    fun findBestMatchingMapKey(map: Map<String, String>, platformVersion: String): String? {
        val (platformVersionNumbers, platformVersionSuffix) = toVersionParts(platformVersion)
        val sortedVersions = map.keys.sortedWith(VersionComparator::compareVersionStrings).reversed()
        for (version in sortedVersions) {
            val (versionNumbers, versionSuffix) = toVersionParts(version)
            val versionComparisonScore = compareVersionParts(versionNumbers, platformVersionNumbers)
            if (versionComparisonScore < 0) {
                return version
            }
            if (versionComparisonScore == 0 && compareVersionSuffix(versionSuffix, platformVersionSuffix) <= 0) {
                return version
            }
        }
        return null
    }

    /**
     * Tells whether [platformVersion] is newer than every entry of [map], i.e. the
     * manually maintained map has fallen behind the IDE actually in use.
     *
     * A newer IDE supports at least what its predecessors did, so callers should
     * fall back to auto selection rather than silently downgrading to the newest
     * known entry, which may be too old to build the project at hand.
     *
     * zh-CN: 判断当前平台版本是否比映射表全部条目都新, 即手动维护的映射表已滞后.
     * 更新的 IDE 至少支持其前代所支持的版本, 因此调用方应回退到 auto 选择,
     * 而不是静默降级到映射表中最新的已知条目 (可能旧到无法构建当前项目).
     */
    fun isPlatformNewerThanVersionMap(
        map: Map<String, String>,
        platformVersion: String,
        defaultVersion: String = Consts.DEFAULT_VERSION,
    ): Boolean {
        if (platformVersion == defaultVersion) return false
        val newestKnownPlatformVersion = map.keys.maxWithOrNull(VersionComparator::compareVersionStrings)
            ?: return false
        return runCatching {
            compareVersionStrings(platformVersion, newestKnownPlatformVersion) > 0
        }.getOrDefault(false)
    }

}
