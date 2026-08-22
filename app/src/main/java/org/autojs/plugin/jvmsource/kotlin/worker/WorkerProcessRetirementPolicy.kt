package org.autojs.plugin.jvmsource.kotlin.worker

/**
 * Pure policy for the disposable worker process. Every terminal path is a direct process
 * retirement; none depends on the main Looper, a user-visible executor, or another queued task.
 */
internal class WorkerProcessRetirementPolicy {
    enum class Trigger {
        TASK_FINISHED,
        INVALID_ADMISSION,
        CALLBACK_DIED,
        PROVIDER_DESTROYED,
        HARD_TERMINATE,
    }

    enum class Action {
        KILL_CURRENT_PROCESS,
    }

    fun action(trigger: Trigger): Action = when (trigger) {
        Trigger.TASK_FINISHED,
        Trigger.INVALID_ADMISSION,
        Trigger.CALLBACK_DIED,
        Trigger.PROVIDER_DESTROYED,
        Trigger.HARD_TERMINATE -> Action.KILL_CURRENT_PROCESS
    }
}
