package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KotlinCompilerPipelineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun compilesPackagedKotlinEntryAndProducesStrictSingleDex() {
        val classpathRoot = File(
            checkNotNull(System.getProperty("autojs.kotlin.compilerClasspathRoot")),
        )
        val androidJar = classpathRoot.resolve("android.jar")
        val entryApiJar = classpathRoot.resolve("entry-api.jar")
        val kotlinStdlibJar = classpathRoot.resolve("kotlin-stdlib.jar")
        val identities = listOf(androidJar, entryApiJar, kotlinStdlibJar).map(ProviderDigests::file)
        val classpath = CompilerClasspath(
            androidJar = androidJar,
            entryApiJar = entryApiJar,
            kotlinStdlibJar = kotlinStdlibJar,
            identities = identities,
            fingerprint = ProviderDigests.combine(CompilerClasspath.COMPILER_CLASSPATH_DOMAIN, identities),
        )
        classpath.verifyInstalled()

        val root = temporaryFolder.newFolder("kotlin-pipeline")
        val source = root.resolve("Main.kt").apply {
            writeText(
                """
                    package smoke.m4

                    import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
                    import org.autojs.plugin.jvmsource.api.JvmScriptContext

                    class Main : AutoJsJvmEntry {
                        override fun run(context: JvmScriptContext): Any =
                            listOf("package", "import").joinToString("/")
                    }
                """.trimIndent(),
            )
        }
        val classes = root.resolve("classes").apply { mkdir() }
        val compilation = KotlinJvmCompiler(classpath).compile(source, classes) {}
        assertTrue(compilation.diagnostics.joinToString("\n") { it.message }, compilation.succeeded)

        val programJar = root.resolve("program.jar")
        val summary = UserClassJarWriter.write(classes, programJar, "smoke.m4.Main")
        assertTrue("Lsmoke/m4/Main;" in summary.dexDescriptors)

        val d8Output = root.resolve("d8").apply { mkdir() }
        val dex = D8JavaCompiler(D8RuntimeLibraries.controlled(classpath)).compile(
            programJar = programJar,
            outputDirectory = d8Output,
            minApi = 24,
            ensureActive = {},
        )
        val bytes = dex.readBytes()
        val validated = DexArtifactValidator.validate(
            bytes = bytes,
            expectedSizeBytes = bytes.size.toLong(),
            expectedSha256 = JvmSha256.digest(bytes),
            requestMinApi = 24,
            deviceApi = 31,
            expectedClassDescriptors = summary.dexDescriptors,
        )

        assertEquals(summary.dexDescriptors, validated.classDescriptors)
        assertEquals(24, validated.requestMinApi)
    }
}
