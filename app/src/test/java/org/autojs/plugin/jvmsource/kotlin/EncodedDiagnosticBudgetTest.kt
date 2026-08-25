package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedDiagnosticBudgetTest {
    private val diagnostic = JvmSourceDiagnostic(
        requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT)),
        severity = JvmDiagnosticSeverity.ERROR,
        code = "ECJ_ERROR",
        message = "错".repeat(1_000),
        sourceFileName = "Main.java",
        line = 1,
        column = 1,
    )

    @Test
    fun budgetsTheCompleteEncodedDiagnosticRatherThanOnlyItsMessage() {
        val encoded = requireNotNull(EncodedDiagnosticBudget.encodeWithin(diagnostic, 256))

        assertTrue(encoded.size <= 256)
        assertNull(EncodedDiagnosticBudget.encodeWithin(diagnostic, 1))
    }

    @Test
    fun preservesChineseAndSupplementaryCodePointsAtTheTruncationBoundary() {
        val original = ("中文🙂🚀").repeat(1_000)
        val encoded = requireNotNull(
            EncodedDiagnosticBudget.encodeWithin(diagnostic.copy(message = original), 512),
        )
        val decoded = JvmSourceCodec.decodeDiagnostic(encoded)
        val prefix = decoded.message.removeSuffix("~")

        assertTrue(encoded.size <= 512)
        assertTrue(decoded.message.endsWith("~"))
        assertTrue(original.startsWith(prefix))
        assertTrue(prefix.isNotEmpty())
        assertFalse(decoded.message.contains('\uFFFD'))
        assertFalse(hasUnpairedSurrogate(decoded.message))
    }

    @Test
    fun exactEncodedSizeDoesNotTriggerTruncation() {
        val compact = diagnostic.copy(message = "中文🙂")
        val full = JvmSourceCodec.encodeDiagnostic(compact)

        assertArrayEquals(full, EncodedDiagnosticBudget.encodeWithin(compact, full.size))
        val shortened = requireNotNull(EncodedDiagnosticBudget.encodeWithin(compact, full.size - 1))
        assertTrue(shortened.size < full.size)
        assertEquals("~", JvmSourceCodec.decodeDiagnostic(shortened).message.takeLast(1))
    }

    private fun hasUnpairedSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                Character.isHighSurrogate(char) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return true
                    index += 2
                }
                Character.isLowSurrogate(char) -> return true
                else -> index++
            }
        }
        return false
    }
}
