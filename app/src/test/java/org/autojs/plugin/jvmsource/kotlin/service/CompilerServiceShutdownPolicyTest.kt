package org.autojs.plugin.jvmsource.kotlin.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilerServiceShutdownPolicyTest {
    @Test
    fun activeCompilerArmsIndependentKillBeforeServiceSchedulerShutdown() {
        val policy = CompilerServiceShutdownPolicy()

        val plan = policy.begin(criticalSessions = 1)

        assertFalse(plan.retireImmediately)
        assertTrue(plan.armIndependentProcessKill)
        assertTrue(plan.graceMillis > 0L)
    }

    @Test
    fun completedCompilerRetiresItsDedicatedProcessImmediately() {
        val policy = CompilerServiceShutdownPolicy()

        val plan = policy.begin(criticalSessions = 0)

        assertTrue(plan.retireImmediately)
        assertFalse(plan.armIndependentProcessKill)
    }

    @Test
    fun handedToWorkerDoesNotSuppressLiveCompilerWatchdog() {
        val policy = CompilerServiceShutdownPolicy()

        assertTrue(policy.requiresSessionWatchdog(compilerThreadLive = true, handedToWorker = false))
        assertTrue(policy.requiresSessionWatchdog(compilerThreadLive = true, handedToWorker = true))
        assertFalse(policy.requiresSessionWatchdog(compilerThreadLive = false, handedToWorker = true))
    }
}
