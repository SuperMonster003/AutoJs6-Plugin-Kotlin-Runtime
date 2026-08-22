package org.autojs.plugin.jvmsource.kotlin.service

/**
 * Pure binding state machine that closes the synchronous ServiceConnection callback race.
 *
 * Android may deliver a connection callback before bindService returns. Cleanup can therefore
 * request an unbind while the return value is still unknown; the caller performs the deferred
 * unbind exactly once after a successful bind result is observed.
 */
internal class WorkerServiceBindingLifecycle {
    enum class State {
        FRESH,
        BINDING,
        BOUND,
        UNBIND_PENDING,
        UNBOUND,
    }

    enum class Action {
        NONE,
        UNBIND_NOW,
    }

    private var state = State.FRESH

    @Synchronized
    fun beginBinding(): Boolean {
        if (state != State.FRESH) return false
        state = State.BINDING
        return true
    }

    @Synchronized
    fun bindingReturned(bound: Boolean): Action = when (state) {
        State.BINDING -> {
            state = if (bound) State.BOUND else State.UNBOUND
            Action.NONE
        }
        State.UNBIND_PENDING -> {
            state = State.UNBOUND
            if (bound) Action.UNBIND_NOW else Action.NONE
        }
        else -> error("bindService result delivered outside a binding attempt")
    }

    @Synchronized
    fun requestUnbind(): Action = when (state) {
        State.BINDING -> {
            state = State.UNBIND_PENDING
            Action.NONE
        }
        State.BOUND -> {
            state = State.UNBOUND
            Action.UNBIND_NOW
        }
        State.FRESH, State.UNBIND_PENDING, State.UNBOUND -> Action.NONE
    }

    @Synchronized
    fun snapshot(): State = state
}
