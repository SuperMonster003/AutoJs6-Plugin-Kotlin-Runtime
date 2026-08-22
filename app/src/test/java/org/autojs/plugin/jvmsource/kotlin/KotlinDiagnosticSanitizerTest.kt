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
