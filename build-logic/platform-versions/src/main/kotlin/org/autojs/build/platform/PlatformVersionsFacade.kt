package org.autojs.build.platform

import java.io.File
import java.util.Properties

/**
 * Entry point for consuming settings scripts, callable from the script body.
 *
 * The settings plugin cannot cover every case on its own. A consuming script has to put
 * AGP on its own buildscript classpath, and that classpath is resolved before any settings
 * plugin is applied, so the versions must be decided earlier still. Calling this from the
 * script body, right after the `buildscript` block that brings it in, gives the same
 * timing the inlined mechanism had.
 *
 * ```kotlin
 * pluginManagement { repositories { mavenLocal(); mavenCentral(); google() } }
 *
 * buildscript {
 *     repositories { mavenLocal(); mavenCentral() }
 *     dependencies { classpath("org.autojs.build:autojs6-gradle-platform-versions:1.0.0") }
 * }
 *
 * val versions = PlatformVersionsFacade.decide(rootDir, gradle.gradleVersion)
 * ```
 *
 * zh-CN: 供消费端 settings 脚本在脚本体中直接调用的入口. 消费端需要把 AGP 放到自己的
 * buildscript classpath 上, 而该 classpath 的解析早于任何 settings 插件的应用,
 * 因此版本必须在更早的时机决定. 在引入本类的 `buildscript` 块之后立即调用,
 * 即可获得与内联机制相同的时序.
 */
object PlatformVersionsFacade {

    /** Runs the full decision for the project rooted at [rootDir]. */
    fun decide(rootDir: File, gradleVersion: String): PlatformVersionsExtension {
        val versionProps = loadProperties(rootDir.resolve("version.properties"))
        val dataSource = DataSource(rootDir.resolve("gradle/data").takeIf { it.isDirectory })
        val platform = PlatformDetector(SystemProperties.ofSystem(), dataSource, versionProps).determine()

        platform.ensureMinimalIdeVersion()
        versionProps["JAVA_VERSION_MIN_SUPPORTED"]?.toIntOrNull()?.let { minSupported ->
            platform.ensureMinimalGradleJdkVersion(currentJavaMajorVersion(), minSupported)
        }

        val versionInfo = mutableListOf<String>()
        platform.prependConsoleInformation(versionInfo)

        val decider = VersionDecider(platform, dataSource, gradleVersion)
        val overriddenJavaVersion = versionProps.escapeHatch("OVERRIDDEN_JAVA_VERSION")?.toIntOrNull()
        val overriddenAgpVersion = versionProps.escapeHatch("OVERRIDDEN_ANDROID_GRADLE_PLUGIN_VERSION")
        val overriddenKotlinVersion = versionProps.escapeHatch("OVERRIDDEN_KOTLIN_GRADLE_PLUGIN_VERSION")
        val overriddenKspVersion = versionProps.escapeHatch("OVERRIDDEN_KSP_GRADLE_PLUGIN_VERSION")

        val kotlinVersion = resolve(
            overridden = overriddenKotlinVersion,
            decide = { decider.decideKotlinVersion() },
            fallback = { null },
            label = "org.jetbrains.kotlin:kotlin-gradle-plugin",
            versionInfo = versionInfo,
        )

        val kspDecider = KspDecider(dataSource, gradleVersion)
        val kspVersion = overriddenKspVersion ?: kspDecider.decideKspVersion(kotlinVersion).version

        val agpVersion = resolve(
            overridden = overriddenAgpVersion,
            decide = { decider.decideAgpVersion() },
            fallback = { decider.agpFallbackVersion() },
            label = "com.android.tools.build:gradle",
            versionInfo = versionInfo,
            postProcess = { candidate ->
                kspDecider.refineAgpVersionForKsp(
                    agpVersion = candidate,
                    kspVersion = kspVersion,
                    isUserSpecified = overriddenAgpVersion != null,
                )
            },
        )

        val r8Decider = R8Decider(dataSource)
        val minimumR8Version = r8Decider.minimumR8VersionFor(kotlinVersion)
        val r8Version = minimumR8Version?.takeIf { r8Decider.shouldUseExternalR8(agpVersion, it) }
        minimumR8Version?.let {
            val notation = "Classpath: \"com.android.tools:r8:$it\""
            versionInfo += when (r8Version) {
                null -> "$notation${Identifier.AGP_BUNDLED_SUFFIX}"
                else -> "$notation${Identifier.AUTO_SPECIFIED_SUFFIX}"
            }
        }

        kspVersion?.let {
            val suffix = when (overriddenKspVersion) {
                null -> Identifier.AUTO_SPECIFIED_SUFFIX
                else -> Identifier.USER_SPECIFIED_SUFFIX
            }
            versionInfo += "Plugin: \"com.google.devtools.ksp:$it\"$suffix"
            System.setProperty("com.google.devtools.ksp", it)
        }

        val maxSupportedJavaVersion = decider.getMaxSupportedJavaVersion(gradleVersion)
        System.setProperty("gradle.java.version.coerced.by.gradle", "$maxSupportedJavaVersion")
        System.setProperty("gradle.java.version.overridden.by.user", "$overriddenJavaVersion")

        val tomlVersions = TomlVersions.read(rootDir.resolve("gradle/libs.versions.toml"))
        SHARED_PLUGIN_VERSION_KEYS.forEach { (pluginId, versionKey) ->
            tomlVersions[versionKey]?.let { System.setProperty(pluginId, it) }
        }

        // Published as properties too, so a consuming script can name them from inside its
        // single buildscript block, where the decided objects are not yet in scope.
        // zh-CN: 同时以属性形式发布, 使消费端脚本能在其唯一的 buildscript 块中引用;
        // 该块内还取不到决策结果对象.
        System.setProperty("gradle.agp.version", agpVersion)
        System.setProperty("gradle.kotlin.version", kotlinVersion)
        kspVersion?.let { System.setProperty("gradle.ksp.version", it) }
        r8Version?.let { System.setProperty("gradle.r8.version", it) }

        return PlatformVersionsExtension(
            platform = platform,
            agpVersion = agpVersion,
            kotlinVersion = kotlinVersion,
            kspVersion = kspVersion,
            r8Version = r8Version,
            maxSupportedJavaVersion = maxSupportedJavaVersion,
            overriddenJavaVersion = overriddenJavaVersion,
            versionInfo = versionInfo.toList(),
        )
    }

    /** Prints the boxed version summary, as the mechanism this replaces did. */
    fun printVersionInfo(versions: PlatformVersionsExtension, gradleVersion: String) {
        Formatted(
            "Version information for IDE platform and Gradle plugins",
            versions.versionInfo,
            footers = listOf("Gradle version: $gradleVersion"),
        ).print()
    }

    /** Plugin id to the version catalog key holding its version. */
    private val SHARED_PLUGIN_VERSION_KEYS = mapOf(
        "org.gradle.toolchains.foojay-resolver-convention" to "foojay-resolver-convention",
    )

    private fun resolve(
        overridden: String?,
        decide: () -> Decision,
        fallback: () -> String?,
        label: String,
        versionInfo: MutableList<String>,
        postProcess: (String) -> Decision = { Decision(it, "") },
    ): String {
        fun report(version: String, hintSuffix: String): String {
            val refined = postProcess(version)
            val finalVersion = refined.version ?: version
            versionInfo += "Classpath: \"$label:$finalVersion\"$hintSuffix${refined.hintSuffix}"
            return finalVersion
        }

        overridden?.let { return report(it, Identifier.USER_SPECIFIED_SUFFIX) }

        val decision = decide()
        decision.version?.let { return report(it, decision.hintSuffix) }

        val fallbackVersion = fallback()
            ?: throw IllegalStateException("Failed to determine version for classpath \"$label\"")
        return report(fallbackVersion, Identifier.FALLBACK_SUFFIX)
    }

    private fun Map<String, String>.escapeHatch(key: String): String? =
        get(key)?.takeUnless { it.isBlank() || it == "NONE" }

    /** Major version of the running JVM, handling both "1.8.0_311" and "21.0.1" forms. */
    private fun currentJavaMajorVersion(): Int {
        val raw = System.getProperty("java.version") ?: return 0
        val head = raw.substringBefore('.').toIntOrNull() ?: return 0
        return when (head) {
            1 -> raw.split('.').getOrNull(1)?.toIntOrNull() ?: 0
            else -> head
        }
    }

    private fun loadProperties(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return Properties().apply { file.inputStream().use { load(it) } } as Map<String, String>
    }

}
