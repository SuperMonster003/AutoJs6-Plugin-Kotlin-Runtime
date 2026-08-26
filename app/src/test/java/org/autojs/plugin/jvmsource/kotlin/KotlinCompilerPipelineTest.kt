package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertFalse
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
        val classpath = CompilerTestFixtures.classpath()
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

    @Test
    fun compilesAndDexesTheControlledCoroutineRuntimeProfile() {
        val classpath = CompilerTestFixtures.classpath()
        classpath.verifyInstalled()
        val root = temporaryFolder.newFolder("coroutine-pipeline")
        val source = root.resolve("Main.kt").apply {
            writeText(CompilerTestFixtures.sample("coroutines.kt"))
        }
        val classes = root.resolve("classes").apply { mkdir() }
        val compilation = KotlinJvmCompiler(classpath).compile(source, classes) {}
        assertTrue(compilation.diagnostics.joinToString("\n") { it.message }, compilation.succeeded)

        val programJar = root.resolve("program.jar")
        val summary = UserClassJarWriter.write(classes, programJar, "samples.m9.Main")
        val d8Output = root.resolve("d8").apply { mkdir() }
        val dex = D8JavaCompiler(D8RuntimeLibraries.controlled(classpath)).compile(
            programJar = programJar,
            outputDirectory = d8Output,
            minApi = 24,
            ensureActive = {},
        )

        assertTrue(dex.isFile)
        assertTrue("Lsamples/m9/Main;" in summary.dexDescriptors)
    }

    @Test
    fun compilerDoesNotExposeReflectOrTheAndroidCoroutineModule() {
        val classpath = CompilerTestFixtures.classpath()
        val root = temporaryFolder.newFolder("excluded-runtime-modules")
        val source = root.resolve("Main.kt").apply {
            writeText(
                """
                    import kotlin.reflect.full.memberProperties
                    import kotlinx.coroutines.android.asCoroutineDispatcher
                    import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
                    import org.autojs.plugin.jvmsource.api.JvmScriptContext

                    class Main : AutoJsJvmEntry {
                        override fun run(context: JvmScriptContext): Any =
                            Main::class.memberProperties.size
                    }
                """.trimIndent(),
            )
        }
        val classes = root.resolve("classes").apply { mkdir() }

        val compilation = KotlinJvmCompiler(classpath).compile(source, classes) {}

        assertFalse(compilation.succeeded)
        val messages = compilation.diagnostics.joinToString("\n") { it.message }
        assertTrue(messages, messages.contains("reflect") || messages.contains("memberProperties"))
        assertTrue(messages, messages.contains("android") || messages.contains("asCoroutineDispatcher"))
    }

    @Test
    fun compilerAndSanitizerKeepFirstLinePositionAfterLeadingBomNormalization() {
        val classpath = CompilerTestFixtures.classpath()
        classpath.verifyInstalled()
        val root = temporaryFolder.newFolder("bom-diagnostic")
        val source = root.resolve("Main.kt")
        val normalized = KotlinSourcePolicy.decodeAndValidate(
            "\uFEFFval value: String = 1".toByteArray(Charsets.UTF_8),
        )
        source.writeText(normalized)
        val classes = root.resolve("classes").apply { mkdir() }

        val compilation = KotlinJvmCompiler(classpath).compile(source, classes) {}
        assertFalse(compilation.succeeded)
        val raw = compilation.diagnostics.firstOrNull { diagnostic ->
            diagnostic.line == 1 && diagnostic.column != null
        } ?: throw AssertionError(
            "Compiler omitted the expected source position: " +
                compilation.diagnostics.joinToString(" | ") { it.toString() },
        )
        val sanitized = KotlinDiagnosticSanitizer.sanitize(
            value = raw,
            byteLimit = 1_024,
            privateFiles = listOf(root),
            sourceFile = source,
        )

        assertEquals("val value: String = 1", normalized)
        assertEquals(1, raw.line)
        assertEquals(19, raw.column)
        assertEquals(1, sanitized.line)
        assertEquals(19, sanitized.column)
    }

}
