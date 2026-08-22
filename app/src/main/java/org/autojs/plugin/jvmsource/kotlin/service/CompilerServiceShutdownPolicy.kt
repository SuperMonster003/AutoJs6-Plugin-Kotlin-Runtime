package org.autojs.plugin.jvmsource.kotlin.service

/** Pure decision boundary for retiring the disposable compiler process. */
internal class CompilerServiceShutdownPolicy {
    data class Plan(
        val armIndependentProcessKill: Boolean,
        val graceMillis: Long,
    )

    fun begin(criticalSessions: Int): Plan {
        require(criticalSessions >= 0)
        return Plan(
            armIndependentProcessKill = criticalSessions > 0,
            graceMillis = COMPILER_SHUTDOWN_GRACE_MILLIS,
        )
    }

    fun shouldKillAfterGrace(criticalSessions: Int): Boolean {
        require(criticalSessions >= 0)
        return criticalSessions > 0
    }

    /** Binding/handoff state never suppresses a watchdog while the compiler thread is live. */
    fun requiresSessionWatchdog(
        compilerThreadLive: Boolean,
        @Suppress("UNUSED_PARAMETER") handedToWorker: Boolean,
    ): Boolean = compilerThreadLive

    private companion object {
        const val COMPILER_SHUTDOWN_GRACE_MILLIS = 2_000L
    }
}
