package org.autojs.build.platform

/**
 * Version string comparison utilities.
 *
 * Extracted verbatim (semantics-wise) from the `utils` object of the AutoJs6
 * settings.gradle.kts version decision mechanism, so that downstream projects
 * keep getting byte-identical decisions after migrating to this plugin.
 *
 * zh-CN: 版本字符串比较工具. 语义完整移植自 AutoJs6 settings.gradle.kts 中的 utils 对象,
 * 以保证下游项目迁移到本插件后, 版本决策结果与迁移前完全一致.
 */
object VersionComparator {

    /**
     * Suffix ranking. Smaller means earlier in the release cycle.
     * An empty suffix (a plain release such as "8.9.3") ranks highest.
     */
    private val suffixPriorityMap = mapOf(
        "canary" to 1, "nightly" to 1, "snapshot" to 1, "dev" to 1,
        "pre-alpha" to 2, "prealpha" to 2, "preview" to 2, "eap" to 2, "milestone" to 2,
        "alpha" to 3,
        "beta" to 4,
        "rc" to 5,
        "" to 10, "stable" to 10, "ga" to 10, "final" to 10, "release" to 10, "lts" to 10,
    )

    private fun normalizeSuffixName(raw: String?): String {
        val n = (raw ?: "").trim().lowercase()
        return when (n) {
            "a" -> "alpha"
            "b" -> "beta"
            "cr" -> "rc"
            "m" -> "milestone"
            "pre" -> "preview"
            else -> n
        }
    }

    /** Compares two version strings, e.g. "2026.2" vs "2025.2.2", or "9.0.1" vs "9.1.0-alpha01". */
    fun compareVersionStrings(v1: String, v2: String): Int {
        val (ver1Numbers, ver1Suffix) = toVersionParts(v1)
        val (ver2Numbers, ver2Suffix) = toVersionParts(v2)
        return compareVersionParts(ver1Numbers, ver2Numbers)
            .takeIf { it != 0 }
            ?: compareVersionSuffix(ver1Suffix, ver2Suffix)
    }

    /** Descending counterpart of [compareVersionStrings], handy as a sorted-map comparator. */
    fun compareVersionStringsDesc(v1: String, v2: String): Int = compareVersionStrings(v2, v1)

    /** Compares the numeric segments, treating missing trailing segments as zero. */
    fun compareVersionParts(parts1: List<Int>, parts2: List<Int>): Int {
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }
            if (part1 != part2) return part1.compareTo(part2)
        }
        return 0
    }

    /** Compares the qualifier segments, e.g. ("rc" to 1) vs ("beta" to 3). */
    fun compareVersionSuffix(suffix1: Pair<String, Int>, suffix2: Pair<String, Int>): Int {
        val (name1Raw, num1) = suffix1
        val (name2Raw, num2) = suffix2
        val name1 = normalizeSuffixName(name1Raw)
        val name2 = normalizeSuffixName(name2Raw)

        val p1 = suffixPriorityMap[name1] ?: Int.MAX_VALUE
        val p2 = suffixPriorityMap[name2] ?: Int.MAX_VALUE

        val byPriority = p1.compareTo(p2)
        if (byPriority != 0) return byPriority
        return num1.compareTo(num2)
    }

    /**
     * Splits a version string into numeric parts plus a qualifier.
     *
     * e.g. "1.2.3-rc1" / "1.2.3 RC 1" / "1.2.3-Alpha" / "1.2.3+m2" / "1.2.3-preview 2".
     *
     * Only whitespace, "+" and "-" separate the numeric part from the qualifier, so a
     * form such as "1.2.3_preview" is rejected rather than parsed.
     */
    fun toVersionParts(version: String): Pair<List<Int>, Pair<String, Int>> {
        val split = version.split(Regex("[\\s+\\-]"), limit = 2)
        val numberStr = split[0]
        val numberParts = numberStr.split('.').map {
            when {
                // Wildcards stand for "any patch", as used by keys such as "2.3.Z" in
                // ksp-releases.properties. They compare as zero.
                // zh-CN: 通配符代表 "任意补丁版本", 如 ksp-releases.properties 中的 "2.3.Z" 键, 比较时视为 0.
                it.matches(Regex("[xyz*?]", RegexOption.IGNORE_CASE)) -> 0
                else -> it.toIntOrNull()
            } ?: throw IllegalArgumentException("Invalid version part: '$it' in version: '$version'")
        }

        val suffixStr = split.getOrNull(1)?.trim().orEmpty()
        if (suffixStr.isEmpty()) return numberParts to ("" to 0)

        val regex = Regex("([A-Za-z]+)[\\s._-]*(\\d*)|([A-Za-z]*)[\\s._-]*(\\d+)", RegexOption.IGNORE_CASE)
        val m = regex.matchEntire(suffixStr) ?: return numberParts to ("" to 0)

        val rawName = (m.groups[1]?.value ?: m.groups[3]?.value).orEmpty()
        val rawNum = (m.groups[2]?.value ?: m.groups[4]?.value).orEmpty()

        val normName = normalizeSuffixName(rawName)
        val suffixNum = rawNum.toIntOrNull() ?: if (normName.isNotEmpty()) 1 else 0

        return numberParts to (normName to suffixNum)
    }

    /** Sorts version names, ascending by default. */
    fun List<String>.sortByVersionName(isDescend: Boolean = false): List<String> {
        return sortedWith { v1, v2 -> compareVersionStrings(v1, v2) * if (isDescend) -1 else 1 }
    }

}
