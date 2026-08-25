import java.util.Properties

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

group = "org.autojs.build"
version = Properties().let { props ->
    rootDir.resolve("version.properties").takeIf { it.isFile }?.inputStream()?.use { props.load(it) }
    props.getProperty("VERSION_NAME") ?: "0.0.0-SNAPSHOT"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    plugins {
        create("platformVersions") {
            id = "org.autojs.build.platform-versions"
            implementationClass = "org.autojs.build.platform.PlatformVersionsSettingsPlugin"
            displayName = "AutoJs6 Platform Versions"
            description = "Auto-selects AGP / Kotlin Gradle plugin versions based on the detected IDE platform, " +
                    "with stale-map fallback and compatibility capping. Extracted from AutoJs6 build scripts."
        }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
