package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
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
}
