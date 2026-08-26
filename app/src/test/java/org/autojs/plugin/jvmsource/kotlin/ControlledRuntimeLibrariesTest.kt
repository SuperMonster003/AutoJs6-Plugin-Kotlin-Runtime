package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCompilerFamily
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmToolchainFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ControlledRuntimeLibrariesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun coroutineArtifactIdentityChangesRuntimeToolchainAndCacheBoundaries() {
        val root = temporaryFolder.newFolder("controlled runtime")
        val files = ControlledFiles.create(root, byteArrayOf(1, 2, 3))
        val before = files.classpath()
        val beforeRuntime = D8RuntimeLibraries.controlled(before)
        val beforeToolchain = toolchain(beforeRuntime.fingerprint)

        assertEquals(
            listOf(
                "android.jar",
                "entry-api.jar",
                "kotlin-stdlib.jar",
                "kotlinx-coroutines-core-jvm.jar",
            ),
            beforeRuntime.files.map(File::getName),
        )
        assertEquals(before.identities, beforeRuntime.identities)
        assertEquals(before.fingerprint, beforeRuntime.fingerprint)
        before.verifyInstalled()

        files.kotlinxCoroutinesCoreJar.writeBytes(byteArrayOf(9, 8, 7, 6))
        val after = files.classpath()
        val afterRuntime = D8RuntimeLibraries.controlled(after)
        val afterToolchain = toolchain(afterRuntime.fingerprint)

        assertNotEquals(beforeRuntime.fingerprint, afterRuntime.fingerprint)
        assertNotEquals(beforeToolchain, afterToolchain)
        assertThrows(JavaProviderFailure::class.java) { before.verifyInstalled() }
    }

    private fun toolchain(runtimeFingerprint: JvmSha256): JvmSha256 =
        JvmToolchainFingerprint.compute(
            language = JvmSourceLanguage.KOTLIN,
            sourceCompilerFamily = JvmSourceCompilerFamily.KOTLIN_JVM,
            sourceCompilerVersion = "2.3.21",
            d8Version = "8.13.17",
            runtimeLibraryFingerprint = runtimeFingerprint,
        )

    private data class ControlledFiles(
        val androidJar: File,
        val entryApiJar: File,
        val kotlinStdlibJar: File,
        val kotlinxCoroutinesCoreJar: File,
    ) {
        fun classpath(): CompilerClasspath {
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
                fingerprint = ProviderDigests.combine(
                    CompilerClasspath.COMPILER_CLASSPATH_DOMAIN,
                    identities,
                ),
            )
        }

        companion object {
            fun create(root: File, coroutineBytes: ByteArray): ControlledFiles = ControlledFiles(
                androidJar = root.resolve("android.jar").apply { writeBytes(byteArrayOf(1)) },
                entryApiJar = root.resolve("entry-api.jar").apply { writeBytes(byteArrayOf(2)) },
                kotlinStdlibJar = root.resolve("kotlin-stdlib.jar").apply { writeBytes(byteArrayOf(3)) },
                kotlinxCoroutinesCoreJar = root.resolve("kotlinx-coroutines-core-jvm.jar").apply {
                    writeBytes(coroutineBytes)
                },
            )
        }
    }
}
