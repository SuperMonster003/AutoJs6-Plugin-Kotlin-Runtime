package org.autojs.plugin.jvmsource.kotlin.worker

/**
 * Process-local, zero-queue worker admission.
 *
 * A terminal worker can never accept another task; recovery requires Android to create a new
 * worker process and therefore a new lifecycle instance.
 */
internal class SingleUseWorkerLifecycle {
    enum class State {
        FRESH,
        RUNNING,
        TERMINAL,
        RETIRED,
    }

    enum class Admission {
        ACQUIRED,
        REJECTED_ACTIVE,
        REJECTED_RETIRED,
    }

    private var state = State.FRESH

    @Synchronized
    fun tryClaim(): Admission = when (state) {
        State.FRESH -> {
            state = State.RUNNING
            Admission.ACQUIRED
        }
        State.RUNNING -> Admission.REJECTED_ACTIVE
        State.TERMINAL, State.RETIRED -> Admission.REJECTED_RETIRED
    }

    @Synchronized
    fun markTerminal(): Boolean {
        if (state != State.RUNNING) return false
        state = State.TERMINAL
        return true
    }

    @Synchronized
    fun retire(): Boolean {
        if (state == State.RETIRED) return false
        state = State.RETIRED
        return true
    }

    @Synchronized
    fun snapshot(): State = state
}
