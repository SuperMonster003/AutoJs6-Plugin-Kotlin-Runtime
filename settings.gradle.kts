enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "autojs6-plugin-kotlin-runtime"

pluginManagement {
    providers.gradleProperty("autojs.buildPlugins.includeBuild").orNull?.let { includeBuild(it) }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("io.github.supermonster003.autojs6-platform-versions") version "1.8.1"
        id("io.github.supermonster003.autojs6-native-alignment") version "1.8.1"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

plugins {
    id("io.github.supermonster003.autojs6-platform-versions")
    // Enable JDK auto-resolution/download capability for build modules.
    id("org.gradle.toolchains.foojay-resolver-convention")
}

includeBuild("build-logic")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")
include(":m7-harness")
