package org.autojs.plugin.jvmsource.kotlin.service

/** Atomically commits terminal state plus payload, then releases it at exact-session retirement. */
internal class ProviderTerminalDeliveryBarrier<T : Any>(
    private val termination: JavaSessionTerminationPolicy,
) {
    enum class State {
        ACTIVE,
        STAGED,
        SILENT_TERMINAL,
        RETIRED,
        DELIVERED,
    }

    data class Retirement<T>(
        val accepted: Boolean,
        val externalTerminal: T?,
    )

    private var state = State.ACTIVE
    private var value: T? = null

    @Synchronized
    fun commitExternal(
        outcome: JavaSessionTerminationPolicy.Outcome,
        terminal: T,
        afterStateTransitionForTest: () -> Unit = {},
    ): Boolean {
        if (state != State.ACTIVE) return false
        if (!termination.tryTerminal(outcome)) return false
        afterStateTransitionForTest()
        value = terminal
        state = State.STAGED
        return true
    }

    @Synchronized
    fun commitSilent(outcome: JavaSessionTerminationPolicy.Outcome): Boolean {
        if (state != State.ACTIVE) return false
        if (!termination.tryTerminal(outcome)) return false
        state = State.SILENT_TERMINAL
        return true
    }

    @Synchronized
    fun retire(): Retirement<T> {
        if (!termination.markCleaned()) return Retirement(accepted = false, externalTerminal = null)
        val terminal = value
        value = null
        state = if (terminal == null) State.RETIRED else State.DELIVERED
        return Retirement(accepted = true, externalTerminal = terminal)
    }

    @Synchronized
    fun snapshot(): State = state
}
