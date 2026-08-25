package org.autojs.build.platform

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.extra

/**
 * Settings plugin deciding the AGP and Kotlin Gradle plugin versions for the build.
 *
 * Apply it from a consuming settings script, above `includeBuild` so that an included
 * build sees the system properties this publishes:
 *
 * ```kotlin
 * pluginManagement {
 *     repositories { mavenLocal(); gradlePluginPortal(); mavenCentral(); google() }
 *     plugins { id("org.autojs.build.platform-versions") version "1.0.0" }
 * }
 *
 * plugins { id("org.autojs.build.platform-versions") }
 * ```
 *
 * A project that also needs AGP on its own buildscript classpath cannot use the plugin
 * alone, because that classpath resolves before any settings plugin is applied. Such a
 * project calls [PlatformVersionsFacade] from the script body instead.
 *
 * zh-CN: 决定构建所用 AGP 与 Kotlin Gradle 插件版本的 Settings 插件.
 * 需在 `includeBuild` 之前应用, 以便 included build 能读到本插件发布的系统属性.
 * 若项目还需要把 AGP 放到自己的 buildscript classpath 上, 仅用本插件无法满足
 * (该 classpath 的解析早于任何 settings 插件的应用), 此时改在脚本体中调用 [PlatformVersionsFacade].
 */
class PlatformVersionsSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val versions = PlatformVersionsFacade.decide(settings.rootDir, settings.gradle.gradleVersion)

        settings.extensions.add(PlatformVersionsExtension::class.java, EXTENSION_NAME, versions)
        settings.gradle.extra.set("platform", versions.platform)
        settings.gradle.extra.set("platformVersions", versions)

        // Also exported as plain strings, because a settings buildscript block is compiled
        // against its own classpath and cannot resolve the extension type.
        // zh-CN: 同时以纯字符串导出: settings 的 buildscript 块使用独立的编译类路径, 无法解析扩展类型.
        settings.gradle.extra.set("agpClasspathNotation", versions.agpClasspathNotation)
        settings.gradle.extra.set("kotlinClasspathNotation", versions.kotlinClasspathNotation)
        settings.gradle.extra.set("r8ClasspathNotation", versions.r8ClasspathNotation)

        settings.gradle.taskGraph.whenReady {
            if (allTasks.none { it.name == "clean" }) {
                PlatformVersionsFacade.printVersionInfo(versions, settings.gradle.gradleVersion)
            }
        }
    }

    companion object {
        const val EXTENSION_NAME = "platformVersions"
    }

}
