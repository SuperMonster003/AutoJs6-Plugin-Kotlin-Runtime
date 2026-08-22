package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JavaRuntimeDiagnosticPolicyTest {
    @Test
    fun extractsOnlyValidatedMainOrInnerClassLine() {
        assertEquals(17, JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "Main.kt", 17)))
        assertEquals(23, JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main\$Inner", "Main.kt", 23)))
        assertEquals(
            29,
            JavaRuntimeDiagnosticPolicy.sourceLine(
                failure("com.example.Main\$Inner", "Main.kt", 29),
                entryClassName = "com.example.Main",
                sourceFileName = "Main.kt",
            ),
        )
    }

    @Test
    fun rejectsPathsOtherClassesAndInvalidLines() {
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Other", "Main.kt", 17)))
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "/private/Main.kt", 17)))
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "Main.kt", -1)))
        assertNull(JavaRuntimeDiagnosticPolicy.sourceLine(failure("Main", "Main.kt", Int.MAX_VALUE)))
    }

    @Test
    fun boundedCauseTraversalFindsSafeLineWithoutUsingMessages() {
        val root = IllegalStateException("C:\\private\\secret signer=${"a".repeat(64)}")
        root.stackTrace = arrayOf(StackTraceElement("Other", "run", "Other.java", 1))
        val cause = failure("Main", "Main.kt", 31)
        root.initCause(cause)

        assertEquals(31, JavaRuntimeDiagnosticPolicy.sourceLine(root))
    }

    private fun failure(className: String, fileName: String, line: Int): Throwable =
        IllegalStateException("sensitive text").apply {
            stackTrace = arrayOf(StackTraceElement(className, "run", fileName, line))
        }
}
