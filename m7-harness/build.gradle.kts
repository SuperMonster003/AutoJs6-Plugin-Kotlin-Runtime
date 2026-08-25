import java.util.Properties
import java.io.File

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

val signingProperties = Properties().apply {
    rootProject.file("sign.properties").inputStream().use(::load)
}

fun signingValue(name: String): String = signingProperties.getProperty(name)?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: throw GradleException("sign.properties is missing '$name'")

val signingStore = File(signingValue("storeFile")).let { configured ->
    if (configured.isAbsolute) configured else rootProject.file("app/${configured.path}")
}.canonicalFile

android {
    namespace = "org.autojs.plugin.jvmsource.kotlin.m7harness"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "org.autojs.plugin.jvmsource.kotlin.m7harness"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("hostAligned") {
            storeFile = signingStore
            storePassword = signingValue("storePassword")
            keyAlias = signingValue("keyAlias")
            keyPassword = signingValue("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("hostAligned")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hostAligned")
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

dependencies {
    implementation(files(rootProject.file("protocol/jvm-source-api.aar")))
    // The harness is loaded into AutoJs6's process, so its runner-side Lifecycle ABI must match
    // the host runtime instead of androidx.test:core's older transitive minimum (2.3.1).
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
