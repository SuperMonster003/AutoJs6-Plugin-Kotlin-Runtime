package org.autojs.plugin.jvmsource.kotlin.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleUseWorkerLifecycleTest {
    @Test
    fun admitsExactlyOneTaskWithNoQueueAndNeverReusesTerminalProcess() {
        val lifecycle = SingleUseWorkerLifecycle()
        assertEquals(SingleUseWorkerLifecycle.Admission.ACQUIRED, lifecycle.tryClaim())
        assertEquals(SingleUseWorkerLifecycle.Admission.REJECTED_ACTIVE, lifecycle.tryClaim())
        assertTrue(lifecycle.markTerminal())
        assertEquals(SingleUseWorkerLifecycle.Admission.REJECTED_RETIRED, lifecycle.tryClaim())
        assertFalse(lifecycle.markTerminal())
        assertTrue(lifecycle.retire())
        assertEquals(SingleUseWorkerLifecycle.State.RETIRED, lifecycle.snapshot())
    }

    @Test
    fun terminalRecoveryRequiresAFreshWorkerLifecycle() {
        val endedProcess = SingleUseWorkerLifecycle()
        assertEquals(SingleUseWorkerLifecycle.Admission.ACQUIRED, endedProcess.tryClaim())
        assertTrue(endedProcess.markTerminal())
        assertEquals(SingleUseWorkerLifecycle.Admission.REJECTED_RETIRED, endedProcess.tryClaim())

        val freshProcess = SingleUseWorkerLifecycle()
        assertEquals(SingleUseWorkerLifecycle.Admission.ACQUIRED, freshProcess.tryClaim())
    }
}
