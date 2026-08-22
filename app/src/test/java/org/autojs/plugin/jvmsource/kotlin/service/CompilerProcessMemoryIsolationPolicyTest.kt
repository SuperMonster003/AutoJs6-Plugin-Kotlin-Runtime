package org.autojs.plugin.jvmsource.kotlin.service

import org.junit.Assert.assertThrows
import org.junit.Test

class CompilerProcessMemoryIsolationPolicyTest {
    @Test
    fun acceptsOnlySuccessfulSetAndObservedNonDumpableState() {
        CompilerProcessMemoryIsolationPolicy.requireDisabled(setResult = 0, observedDumpable = 0)

        assertThrows(IllegalArgumentException::class.java) {
            CompilerProcessMemoryIsolationPolicy.requireDisabled(setResult = -1, observedDumpable = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompilerProcessMemoryIsolationPolicy.requireDisabled(setResult = 0, observedDumpable = 1)
        }
    }
}
