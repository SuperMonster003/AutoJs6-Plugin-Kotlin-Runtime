package org.autojs.plugin.jvmsource.kotlin

import java.io.File

internal object CompilerTestFixtures {
    fun classpath(): CompilerClasspath {
        val root = File(
            checkNotNull(System.getProperty("autojs.kotlin.compilerClasspathRoot")),
        )
        val androidJar = root.resolve("android.jar")
        val entryApiJar = root.resolve("entry-api.jar")
        val kotlinStdlibJar = root.resolve("kotlin-stdlib.jar")
        val kotlinxCoroutinesCoreJar = root.resolve("kotlinx-coroutines-core-jvm.jar")
        val identities = listOf(
            androidJar,
            entryApiJar,
            kotlinStdlibJar,
            kotlinxCoroutinesCoreJar,
        ).map(ProviderDigests::file)
        return CompilerClasspath(
            androidJar = androidJar,
            entryApiJar = entryApiJar,
            kotlinStdlibJar = kotlinStdlibJar,
            kotlinxCoroutinesCoreJar = kotlinxCoroutinesCoreJar,
            identities = identities,
            fingerprint = ProviderDigests.combine(CompilerClasspath.COMPILER_CLASSPATH_DOMAIN, identities),
        )
    }

    fun errorSample(fileName: String): String {
        val root = File(checkNotNull(System.getProperty("autojs.kotlin.errorSamplesRoot")))
        return root.resolve(fileName).readText(Charsets.UTF_8)
    }

    fun sample(fileName: String): String {
        val root = File(checkNotNull(System.getProperty("autojs.kotlin.samplesRoot")))
        return root.resolve(fileName).readText(Charsets.UTF_8)
    }
}
