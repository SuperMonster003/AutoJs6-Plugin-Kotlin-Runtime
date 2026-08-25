enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "autojs6-plugin-kotlin-runtime"

pluginManagement {
    // Keep the platform-version decision logic reproducible in clean CI/archive builds instead of
    // relying on an unpublished artifact from the developer machine's Maven Local repository.
    includeBuild("build-logic/platform-versions")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.autojs.build.platform-versions") version "1.4.1"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

plugins {
    id("org.autojs.build.platform-versions")
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
