package org.autojs.plugin.jvmsource.kotlin.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilerServiceShutdownPolicyTest {
    @Test
    fun activeCompilerArmsIndependentKillBeforeServiceSchedulerShutdown() {
        val policy = CompilerServiceShutdownPolicy()

        val plan = policy.begin(criticalSessions = 1)

        assertTrue(plan.armIndependentProcessKill)
        assertTrue(plan.graceMillis > 0L)
        assertTrue(policy.shouldKillAfterGrace(criticalSessions = 1))
    }

    @Test
    fun completedCompilerNeedsNeitherFallbackKillNorResidualGateReset() {
        val policy = CompilerServiceShutdownPolicy()

        assertFalse(policy.begin(criticalSessions = 0).armIndependentProcessKill)
        assertFalse(policy.shouldKillAfterGrace(criticalSessions = 0))
    }

    @Test
    fun handedToWorkerDoesNotSuppressLiveCompilerWatchdog() {
        val policy = CompilerServiceShutdownPolicy()

        assertTrue(policy.requiresSessionWatchdog(compilerThreadLive = true, handedToWorker = false))
        assertTrue(policy.requiresSessionWatchdog(compilerThreadLive = true, handedToWorker = true))
        assertFalse(policy.requiresSessionWatchdog(compilerThreadLive = false, handedToWorker = true))
    }
}
