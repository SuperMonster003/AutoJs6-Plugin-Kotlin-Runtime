package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinErrorSamplesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingImportAndTypeMismatchSamplesProduceLocatedPublicDiagnostics() {
        listOf(
            ExpectedCompilerError("missing-import.kt", line = 6, messageToken = "UUID"),
            ExpectedCompilerError("type-mismatch.kt", line = 5, messageToken = "mismatch"),
        ).forEach { expected ->
            val root = temporaryFolder.newFolder(expected.fileName.removeSuffix(".kt"))
            val source = root.resolve("Main.kt").apply {
                writeText(CompilerTestFixtures.errorSample(expected.fileName), Charsets.UTF_8)
            }
            val classes = root.resolve("classes").apply { mkdir() }
            val compilation = KotlinJvmCompiler(CompilerTestFixtures.classpath()).compile(source, classes) {}

            assertFalse(expected.fileName, compilation.succeeded)
            val raw = compilation.diagnostics.firstOrNull { diagnostic ->
                diagnostic.severity == JvmDiagnosticSeverity.ERROR && diagnostic.line != null
            } ?: throw AssertionError("${expected.fileName} produced no located error: ${compilation.diagnostics}")
            val sanitized = KotlinDiagnosticSanitizer.sanitize(
                value = raw,
                byteLimit = 1_024,
                privateFiles = listOf(root),
                sourceFile = source,
            )

            assertEquals(expected.fileName, expected.line, raw.line)
            assertTrue(expected.fileName, raw.column != null && raw.column > 0)
            assertTrue(expected.fileName, raw.message.contains(expected.messageToken, ignoreCase = true))
            assertEquals("KOTLIN_ERROR", sanitized.code)
            assertEquals(raw.line, sanitized.line)
            assertEquals(raw.column, sanitized.column)
            assertFalse(sanitized.message.contains(root.absolutePath))
        }
    }

    @Test
    fun missingEntryInterfaceSampleReachesTheStableEntryDiagnostic() {
        val root = temporaryFolder.newFolder("entry-interface-missing")
        val source = root.resolve("Main.kt").apply {
            writeText(CompilerTestFixtures.errorSample("entry-interface-missing.kt"), Charsets.UTF_8)
        }
        val classes = root.resolve("classes").apply { mkdir() }
        val compilation = KotlinJvmCompiler(CompilerTestFixtures.classpath()).compile(source, classes) {}
        assertTrue(compilation.diagnostics.joinToString("\n") { it.message }, compilation.succeeded)

        val failure = assertThrows(JavaProviderFailure::class.java) {
            UserClassJarWriter.write(classes, root.resolve("program.jar"), "Main")
        }

        assertEquals(JvmSourceErrorCode.ENTRY_POINT_MISSING, failure.code)
        assertEquals(JvmSourceFailurePhase.COMPILATION, failure.phase)
        assertEquals("Requested entry class must implement AutoJsJvmEntry", failure.message)
    }

    @Test
    fun packageMismatchSampleFailsAtInputWithThePublicDecisionMessage() {
        val source = CompilerTestFixtures.errorSample("package-entry-mismatch.kt")

        val failure = assertThrows(JavaProviderFailure::class.java) {
            KotlinSourcePolicy.decodeAndValidate(
                source.toByteArray(Charsets.UTF_8),
                sourceFileName = "Main.kt",
                entryClassName = "sample.claimed.Main",
            )
        }

        assertEquals(JvmSourceErrorCode.INVALID_REQUEST, failure.code)
        assertEquals(JvmSourceFailurePhase.INPUT, failure.phase)
        assertEquals(KotlinSourcePolicy.PACKAGE_ENTRY_MISMATCH_MESSAGE, failure.publicMessage)
    }

    private data class ExpectedCompilerError(
        val fileName: String,
        val line: Int,
        val messageToken: String,
    )
}
