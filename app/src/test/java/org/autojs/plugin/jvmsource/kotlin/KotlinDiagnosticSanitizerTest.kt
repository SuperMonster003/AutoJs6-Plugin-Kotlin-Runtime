package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinDiagnosticSanitizerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun keepsActionableCompilerMessageAndSourceLocation() {
        val workspace = temporaryFolder.newFolder("provider-private-root")
        val source = workspace.resolve("Main.kt")
        val diagnostic = KotlinDiagnosticSanitizer.sanitize(
            value = RawKotlinDiagnostic(
                severity = JvmDiagnosticSeverity.ERROR,
                message = "Unresolved reference 'println2'.",
                sourcePath = source.absolutePath,
                line = 7,
                column = 20,
            ),
            byteLimit = 1_024,
            privateFiles = listOf(workspace),
            sourceFile = source,
        )

        assertEquals("KOTLIN_ERROR", diagnostic.code)
        assertEquals(7, diagnostic.line)
        assertEquals(20, diagnostic.column)
        assertTrue("println2" in diagnostic.message)
        assertFalse(workspace.absolutePath in diagnostic.message)
    }

    @Test
    fun leadingBomNormalizationKeepsTheFirstLineCompilerCoordinateUnshifted() {
        val workspace = temporaryFolder.newFolder("provider-bom-root")
        val source = workspace.resolve("Main.kt")
        val normalized = KotlinSourcePolicy.decodeAndValidate(
            "\uFEFFval value: String = 1".toByteArray(Charsets.UTF_8),
        )
        source.writeText(normalized)

        val diagnostic = KotlinDiagnosticSanitizer.sanitize(
            value = RawKotlinDiagnostic(
                severity = JvmDiagnosticSeverity.ERROR,
                message = "Initializer type mismatch: expected 'String', actual 'Int'.",
                sourcePath = source.absolutePath,
                line = 1,
                column = 19,
            ),
            byteLimit = 1_024,
            privateFiles = listOf(workspace),
            sourceFile = source,
        )

        assertEquals("val value: String = 1", source.readText())
        assertEquals(1, diagnostic.line)
        assertEquals(19, diagnostic.column)
        assertEquals("KOTLIN_ERROR", diagnostic.code)
    }

    @Test
    fun canonicalPathEquivalenceKeepsLocationsAcrossSeparatorForms() {
        val workspace = temporaryFolder.newFolder("provider-separator-root")
        val source = workspace.resolve("Main.kt").apply { writeText("class Main") }
        val alternateSeparators = source.absolutePath.replace('\\', '/')

        val diagnostic = KotlinDiagnosticSanitizer.sanitize(
            value = RawKotlinDiagnostic(
                severity = JvmDiagnosticSeverity.ERROR,
                message = "Source diagnostic",
                sourcePath = alternateSeparators,
                line = 3,
                column = 5,
            ),
            byteLimit = 1_024,
            privateFiles = listOf(workspace),
            sourceFile = source,
        )

        assertEquals(3, diagnostic.line)
        assertEquals(5, diagnostic.column)
    }

    @Test
    fun removesPrivatePathsAndIdentityTokens() {
        val workspace = temporaryFolder.newFolder("provider-sensitive-root")
        val source = workspace.resolve("Main.kt")
        val diagnostic = KotlinDiagnosticSanitizer.sanitize(
            value = RawKotlinDiagnostic(
                severity = JvmDiagnosticSeverity.ERROR,
                message = "classpath=${workspace.absolutePath} signer=${"a".repeat(64)} uid=1000",
                sourcePath = source.absolutePath,
                line = 1,
                column = 1,
            ),
            byteLimit = 256,
            privateFiles = listOf(workspace),
            sourceFile = source,
        )

        assertEquals("Kotlin compilation error", diagnostic.message)
        listOf("classpath", "signer", "uid", workspace.absolutePath).forEach {
            assertFalse(it in diagnostic.message)
        }
    }

    @Test
    fun multiByteMessageRespectsRequestedUtf8BudgetAndForeignLocationIsOmitted() {
        val workspace = temporaryFolder.newFolder("provider-budget-root")
        val source = workspace.resolve("Main.kt")
        val diagnostic = KotlinDiagnosticSanitizer.sanitize(
            value = RawKotlinDiagnostic(
                severity = JvmDiagnosticSeverity.WARNING,
                message = "错".repeat(1_000),
                sourcePath = workspace.resolve("kotlin-stdlib.jar").absolutePath,
                line = 4,
                column = 2,
            ),
            byteLimit = 64,
            privateFiles = emptyList(),
            sourceFile = source,
        )

        assertTrue(diagnostic.message.toByteArray(Charsets.UTF_8).size <= 64)
        assertEquals(null, diagnostic.line)
        assertEquals(null, diagnostic.column)
    }
}
