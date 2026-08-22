package org.autojs.plugin.jvmsource.kotlin.service

/** Prevents worker death from acknowledging retirement before provider resources are closed. */
internal class ProviderResourceCleanupBarrier {
    enum class State {
        ACTIVE,
        CLOSING,
        RESOURCES_READY,
    }

    private var state = State.ACTIVE
    private var workerDeathObserved = false

    @Synchronized
    fun beginCleanup(): Boolean {
        if (state != State.ACTIVE) return false
        state = State.CLOSING
        return true
    }

    /** Returns true only when resource cleanup was already published as complete. */
    @Synchronized
    fun observeWorkerDeath(): Boolean {
        workerDeathObserved = true
        return state == State.RESOURCES_READY
    }

    /**
     * Publishes descriptor/workspace closure. Returns whether exact-session finalization may run.
     */
    @Synchronized
    fun resourcesClosed(waitForWorkerDeath: Boolean): Boolean {
        check(state == State.CLOSING)
        state = State.RESOURCES_READY
        return !waitForWorkerDeath || workerDeathObserved
    }

    @Synchronized
    fun snapshot(): State = state
}
