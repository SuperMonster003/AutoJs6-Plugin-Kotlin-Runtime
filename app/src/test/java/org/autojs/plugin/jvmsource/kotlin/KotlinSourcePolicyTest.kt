package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KotlinSourcePolicyTest {
    @Test
    fun acceptsDefaultPackageImportsAndDeclarationWordsInsideCommentsAndLiterals() {
        val source = """
            /* package hidden */
            import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
            class Main : AutoJsJvmEntry {
                val text = "package hidden.again"
                override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any? = null
            }
        """.trimIndent()

        assertEquals(source, KotlinSourcePolicy.decodeAndValidate(source.toByteArray()))
    }

    @Test
    fun acceptsPackageAndImportsWhenTheyMatchRequestedEntry() {
        val source = """
            @file:Suppress("unused")
            package smoke.m4

            import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
            import org.autojs.plugin.jvmsource.api.JvmScriptContext

            class Main : AutoJsJvmEntry {
                override fun run(context: JvmScriptContext): Any = listOf("package", "import")
            }
        """.trimIndent()

        assertEquals(
            source,
            KotlinSourcePolicy.decodeAndValidate(
                source.toByteArray(),
                sourceFileName = "Main.kt",
                entryClassName = "smoke.m4.Main",
            ),
        )
    }

    @Test
    fun acceptsAnAlternativeAsciiEntryNameCarriedByTheProtocolRequest() {
        val source = """
            package smoke.flexible

            class ScriptEntry
        """.trimIndent()

        assertEquals(
            source,
            KotlinSourcePolicy.decodeAndValidate(
                source.toByteArray(),
                sourceFileName = "ScriptEntry.kt",
                entryClassName = "smoke.flexible.ScriptEntry",
            ),
        )
        assertEquals(
            KotlinSourceLayout("ScriptEntry.kt", "smoke.flexible.ScriptEntry"),
            KotlinSourcePolicy.inspect(source.toByteArray(), "ScriptEntry").layout,
        )
    }

    @Test
    fun stripsOnlyInitialUtf8BomAndNormalizesWhitespaceAroundPackageDots() {
        val source = "\uFEFFpackage smoke . m4\nclass Main"
        val inspection = KotlinSourcePolicy.inspect(source.toByteArray())

        assertEquals("package smoke . m4\nclass Main", inspection.normalizedSource)
        assertEquals("smoke.m4.Main", inspection.layout.entryClassName)
        assertEquals("Main.kt", inspection.layout.sourceFileName)
    }

    @Test
    fun rejectsMalformedUtf8NulDuplicatePackagesAndUnterminatedLexicalForms() {
        listOf(
            "class Main\u0000".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
            "package first\npackage second\nclass Main".toByteArray(),
            "/* unterminated".toByteArray(),
        ).forEach { source ->
            assertThrows(JavaProviderFailure::class.java) {
                KotlinSourcePolicy.decodeAndValidate(source)
            }
        }
    }

    @Test
    fun escapedAndNonAsciiPackagesFailWithTheExplicitAsciiOnlyDiagnostic() {
        listOf(
            "package `escaped-name`\nclass Main",
            "package 中文.脚本\nclass Main",
            "package ordinary.`escaped`\nclass Main",
        ).forEach { source ->
            val failure = assertThrows(JavaProviderFailure::class.java) {
                KotlinSourcePolicy.decodeAndValidate(source.toByteArray())
            }

            assertEquals(KotlinSourcePolicy.ASCII_PACKAGE_MESSAGE, failure.message)
            assertEquals(KotlinSourcePolicy.ASCII_PACKAGE_MESSAGE, failure.publicMessage)
        }
    }

    @Test
    fun packageAndRequestedEntryMismatchHasAStablePublicDiagnostic() {
        val failure = assertThrows(JavaProviderFailure::class.java) {
            KotlinSourcePolicy.decodeAndValidate(
                "package actual\nclass Main".toByteArray(),
                sourceFileName = "Main.kt",
                entryClassName = "claimed.Main",
            )
        }

        assertEquals(KotlinSourcePolicy.PACKAGE_ENTRY_MISMATCH_MESSAGE, failure.message)
        assertEquals(KotlinSourcePolicy.PACKAGE_ENTRY_MISMATCH_MESSAGE, failure.publicMessage)
    }

    @Test
    fun nestedCommentsRawStringsAndCharacterLiteralsDoNotAffectPackageInspection() {
        val source = "/* outer /* package hidden */ still outer */\n" +
            "package real.source\n" +
            "val raw = \"\"\"package fake.source\"\"\"\n" +
            "val character = 'p'\n" +
            "class Main"

        assertEquals("real.source.Main", KotlinSourcePolicy.inspect(source.toByteArray()).layout.entryClassName)
    }
}
