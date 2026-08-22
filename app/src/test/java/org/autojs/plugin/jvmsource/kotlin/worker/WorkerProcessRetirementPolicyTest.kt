package org.autojs.plugin.jvmsource.kotlin.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerProcessRetirementPolicyTest {
    @Test
    fun everyTerminalTriggerRequiresDirectProcessKillWithoutMainLooper() {
        val policy = WorkerProcessRetirementPolicy()

        WorkerProcessRetirementPolicy.Trigger.entries.forEach { trigger ->
            assertEquals(
                WorkerProcessRetirementPolicy.Action.KILL_CURRENT_PROCESS,
                policy.action(trigger),
            )
        }
    }
}
