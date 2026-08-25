package org.autojs.build.platform

/**
 * A build host: an IDE, a JDK vendor, or a bare operating system.
 *
 * The platform in use decides which AGP version map applies, since each IDE only
 * supports AGP up to a given version.
 *
 * zh-CN: 构建宿主, 可以是 IDE / JDK 发行方 / 裸操作系统.
 * 当前平台决定适用哪张 AGP 版本映射表, 因为每个 IDE 支持的 AGP 版本有上限.
 */
open class Platform(
    var name: String,
    val vendor: String,
    val agpVersionMap: Map<String, String> = emptyMap(),
    val weight: Int = -Int.MAX_VALUE,
    val gradleSettingsName: String? = null,
    val minSupportedVersion: String = Consts.DEFAULT_VERSION,
    val shouldPrintProgress: Boolean = true,
    val displayName: String? = null,
) {

    var version: String = Consts.DEFAULT_VERSION

    open val fullName: String
        get() = displayName ?: name.replaceFirstChar { it.uppercase() }

    /** Tells whether the running environment looks like this platform. */
    open fun matchEnvironment(systemProperties: SystemProperties): Boolean =
        systemProperties.platform?.startsWith(name) == true
                || systemProperties.vendorName?.contains(vendor, true) == true

    /** Fails when the IDE predates the minimum this project supports. */
    fun ensureMinimalIdeVersion() {
        if (minSupportedVersion == Consts.DEFAULT_VERSION) return
        if (VersionComparator.compareVersionStrings(version, minSupportedVersion) >= 0) return
        throw IllegalStateException(
            "Current IDE ($fullName) version $version does not meet the minimum requirement which $minSupportedVersion is needed"
        )
    }

    /** Fails when the Gradle JDK predates the minimum this project supports. */
    fun ensureMinimalGradleJdkVersion(currentJavaMajorVersion: Int, javaVersionMinSupported: Int) {
        if (currentJavaMajorVersion >= javaVersionMinSupported) return
        throw IllegalStateException(
            Formatted(
                "Current Gradle JDK version $currentJavaMajorVersion does not meet " +
                        "the minimum requirement which $javaVersionMinSupported is needed",
                buildList {
                    gradleSettingsName?.let { add("Settings path: File | Settings | Build, Execution, Deployment | Build Tools | Gradle") }
                    add("Change \"${gradleSettingsName ?: "Gradle JDK"}\" to $javaVersionMinSupported at the least")
                },
            ).text
        )
    }

    /** Prepends the platform banner line to the console summary. */
    fun prependConsoleInformation(consoleInfo: MutableList<String>) {
        val versionSuffix = when (version.isNotEmpty() && version != Consts.DEFAULT_VERSION) {
            true -> " | $version"
            else -> ""
        }
        consoleInfo.add(0, "Platform: $fullName$versionSuffix")
    }

}

/** The subset of system properties that identifies the build host. */
class SystemProperties(private val lookup: (String) -> String?) {

    val version: String? get() = lookup("idea.version")

    val platform: String?
        get() = lookup("idea.paths.selector")
            ?: lookup("idea.platform.prefix")
            ?: lookup("java.vendor.version")

    val vendorName: String?
        get() = lookup("idea.vendor.name")
            ?: lookup("java.vendor")
            ?: lookup("java.vm.vendor")

    val osName: String? get() = lookup("os.name")

    val androidStudioVersion: String? get() = lookup("android.studio.version")

    companion object {
        /** Reads from the real JVM system properties. */
        fun ofSystem() = SystemProperties { System.getProperty(it) }
    }

}
