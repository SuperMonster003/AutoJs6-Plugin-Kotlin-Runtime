package org.autojs.build.platform

/**
 * Identifies the build host and resolves its version.
 *
 * The IntelliJ IDEA and Temurin maps are maintained by hand here, whereas the
 * Android Studio map comes from scraped data. All three benefit from the
 * stale-map fallback in [VersionDecider], so a missing entry degrades to auto
 * selection rather than to a silent downgrade.
 *
 * zh-CN: 识别构建宿主并解析其版本. IntelliJ IDEA 与 Temurin 的映射表在此手动维护,
 * Android Studio 的映射表来自抓取数据. 三者都受益于 [VersionDecider] 的 stale-map 回退:
 * 条目缺失时退化为 auto 选择, 而不是静默降级.
 */
class PlatformDetector(
    private val systemProperties: SystemProperties,
    private val dataSource: DataSource,
    private val versionProps: Map<String, String>,
) {

    /**
     * @Reference AGP Upgrade Assistant integrated within JetBrains IntelliJ IDEA.
     *
     * The newest entry has to state the newest IDE's real ceiling, not just the oldest
     * supported one. Falling off the end of this map means auto selection, which is
     * bounded only by what the running Gradle can load: on Gradle 9.6.1 that reaches
     * AGP 9.2.1, which IDEA 2026.2 rejects with "Latest supported version is AGP 9.1.0".
     *
     * zh-CN: 最新条目必须写出最新 IDE 的真实上限, 而不只是保留最旧的受支持版本.
     * 越过表尾即进入 auto 选择, 其上界只受当前 Gradle 约束: Gradle 9.6.1 下会取到 AGP 9.2.1,
     * 而 IDEA 2026.2 会以 "Latest supported version is AGP 9.1.0" 拒绝它.
     */
    private val intelliJIdeaAgpVersionMap = mapOf(
        "2026.2" to "9.1.0", /* Reported by the IDE itself as its latest supported AGP. */
        "2026.1.2" to "9.0.1",
    )

    /**
     * Eclipse Adoptium, more commonly known as Temurin.
     *
     * Empty since dropping Gradle 8: every entry this map ever held pointed at an AGP
     * on the 8.x line. An IDE-less build therefore takes the auto-selection path, which
     * lands on the newest AGP the running Gradle can load.
     *
     * zh-CN: 移除 Gradle 8 支持后此表为空: 其历史条目全部指向 8.x 线的 AGP.
     * 因此无 IDE 的构建会走 auto 选择, 得到当前 Gradle 能加载的最新 AGP.
     */
    private val temurinAgpVersionMap = emptyMap<String, String>()

    private val androidStudio by lazy {
        object : Platform(
            name = "AndroidStudio",
            vendor = "Google",
            agpVersionMap = dataSource.props("android-studio-agp-compat"),
            weight = Int.MAX_VALUE,
            gradleSettingsName = "Gradle JDK",
            minSupportedVersion = versionProps["MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION"] ?: Consts.DEFAULT_VERSION,
        ) {
            override val fullName: String
                get() {
                    val codenameVersions = dataSource.props("android-studio-codename-version")
                    val codenames = dataSource.props("android-studio-codename")
                    val suffix = codenameVersions.let { prop ->
                        val letters = prop[version]
                            ?: prop[version.split(".").take(3).joinToString(".")]
                            ?: prop[version.split(".").take(2).joinToString(".")]
                            ?: return@let null
                        letters.split("|").joinToString(" / ", prefix = " ") { key ->
                            codenames[key.trim()] ?: key
                        }
                    } ?: ""
                    return "Android Studio$suffix"
                }
        }
    }

    private val intelliJIdea by lazy {
        Platform(
            name = "IntelliJIdea",
            vendor = "Jetbrains",
            agpVersionMap = intelliJIdeaAgpVersionMap,
            weight = 10,
            gradleSettingsName = "Gradle JVM",
            minSupportedVersion = versionProps["MIN_SUPPORTED_INTELLIJ_IDEA_IDE_VERSION"] ?: Consts.DEFAULT_VERSION,
            displayName = "IntelliJ IDEA",
        )
    }

    private val temurin by lazy {
        Platform(
            name = "Temurin",
            vendor = "temurin",
            agpVersionMap = temurinAgpVersionMap,
            weight = 5,
            shouldPrintProgress = false,
        )
    }

    private val unknown by lazy { Platform(name = "Unknown", vendor = "unknown") }

    /** All known platforms, most specific first. */
    private val candidates by lazy { listOf(androidStudio, intelliJIdea, temurin) }

    /**
     * Picks the platform in use and fills in its version.
     *
     * With several matches, the heaviest wins: Android Studio reports itself as a
     * JetBrains product too, so its weight outranks IntelliJ IDEA.
     */
    fun determine(): Platform {
        val matched = candidates.filter { it.matchEnvironment(systemProperties) }
        val platform = when {
            matched.isEmpty() -> unknown.also { it.name = systemProperties.osName ?: it.name }
            matched.size > 1 -> matched.maxBy { it.weight }
            else -> matched.first()
        }
        platform.version = when (platform) {
            androidStudio -> parseAndroidStudioBuildToVersion() ?: parseVersion(platform)
            else -> parseVersion(platform)
        }
        return platform
    }

    private fun parseVersion(platform: Platform): String {
        systemProperties.version?.let { return it }
        val raw = systemProperties.platform
        if (platform === unknown || raw == null) return Consts.DEFAULT_VERSION
        return raw.substring(platform.name.length)
            .replace(Regex("^\\W*"), "")
            .replace(Regex("^Preview", RegexOption.IGNORE_CASE), "")
    }

    /**
     * Turns an Android Studio build number into a marketing version.
     *
     * e.g. "251.26094.121.2513.13991806" becomes "2025.1.3".
     */
    fun parseAndroidStudioBuildToVersion(): String? {
        val build = systemProperties.androidStudioVersion ?: return null

        dataSource.props("android-studio-build-version")[build]?.let { return it }

        val parts = build.split('.')
        val baseStr = parts.getOrNull(0) ?: return null
        val base = baseStr.toIntOrNull() ?: return null

        val year = 2000 + base / 10
        val minor = base % 10

        // Look for strings starting with base and having longer length in the remaining fragments.
        // e.g. "2513" means patch version is "3".
        // zh-CN: 在其余片段里找以 base 开头且长度更长的字段. 如 "2513" 意味着补丁版本为 "3".
        val patch = parts.drop(1).firstNotNullOfOrNull { seg ->
            if (seg.startsWith(baseStr) && seg.length > baseStr.length) {
                seg.substring(baseStr.length).toIntOrNull()
            } else null
        }

        return if (patch != null && patch > 0) "$year.$minor.$patch" else "$year.$minor"
    }

}
