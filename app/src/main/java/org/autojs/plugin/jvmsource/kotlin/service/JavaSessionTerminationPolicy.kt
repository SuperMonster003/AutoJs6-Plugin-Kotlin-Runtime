package org.autojs.plugin.jvmsource.kotlin.service

import org.autojs.plugin.jvmsource.api.JvmCancellationReason

/**
 * Pure state machine for the compiler-session terminal race.
 *
 * A cancellation request wins over a racing success/failure. When a worker is live the caller
 * must signal cancellation first, wait the finite grace, and only then hard-stop the disposable
 * worker. There is no path back to ACTIVE after a terminal.
 */
internal class JavaSessionTerminationPolicy {
    enum class State {
        ACTIVE,
        CANCELLATION_REQUESTED,
        TERMINAL,
        CLEANED,
    }

    enum class Outcome {
        SUCCESS,
        COMPILATION_ERROR,
        RUNTIME_ERROR,
        WORKER_DIED,
        CANCELLED,
        HOST_DIED,
        PROVIDER_DESTROYED,
    }

    enum class DispatchState {
        IDLE,
        RESERVED,
        SUBMITTED,
        FAILED,
    }

    data class CancellationPlan(
        val accepted: Boolean,
        val reason: JvmCancellationReason,
        val interruptCompiler: Boolean,
        val signalWorker: Boolean,
        val scheduleHardStop: Boolean,
        val waitForDispatchCompletion: Boolean,
    )

    data class DispatchCompletionPlan(
        val cancellationReason: JvmCancellationReason?,
        val signalWorker: Boolean,
        val scheduleHardStop: Boolean,
        val immediateHardStop: Boolean,
        val finishCancellationWithoutWorker: Boolean,
    )

    data class Snapshot(
        val state: State,
        val cancellationReason: JvmCancellationReason?,
        val outcome: Outcome?,
        val hardStopIssued: Boolean,
        val dispatchState: DispatchState,
    ) {
        val terminal: Boolean
            get() = state == State.TERMINAL || state == State.CLEANED
    }

    private var state = State.ACTIVE
    private var cancellationReason: JvmCancellationReason? = null
    private var outcome: Outcome? = null
    private var hardStopRequired = false
    private var hardStopIssued = false
    private var dispatchState = DispatchState.IDLE

    @Synchronized
    fun requestCancellation(reason: JvmCancellationReason, workerLive: Boolean): CancellationPlan {
        val effectiveReason = cancellationReason ?: reason
        if (state != State.ACTIVE) {
            return CancellationPlan(false, effectiveReason, false, false, false, false)
        }
        val dispatchReserved = dispatchState == DispatchState.RESERVED
        cancellationReason = reason
        hardStopRequired = workerLive || dispatchReserved
        state = State.CANCELLATION_REQUESTED
        return CancellationPlan(
            accepted = true,
            reason = reason,
            interruptCompiler = true,
            signalWorker = workerLive && !dispatchReserved,
            scheduleHardStop = workerLive && !dispatchReserved,
            waitForDispatchCompletion = dispatchReserved,
        )
    }

    @Synchronized
    fun canDispatchWorkerArtifacts(): Boolean = state == State.ACTIVE && dispatchState == DispatchState.IDLE

    /**
     * Reserves the sole dispatch without performing Binder I/O under this policy's monitor.
     */
    @Synchronized
    fun reserveWorkerDispatch(): Boolean {
        if (!canDispatchWorkerArtifacts()) return false
        dispatchState = DispatchState.RESERVED
        return true
    }

    /**
     * Completes a two-phase dispatch. Cancellation admitted while RESERVED is intentionally
     * deferred so the submitter can enqueue execute first and then issue cancel in-order.
     */
    @Synchronized
    fun completeWorkerDispatch(submitted: Boolean): DispatchCompletionPlan {
        check(dispatchState == DispatchState.RESERVED)
        dispatchState = if (submitted) DispatchState.SUBMITTED else DispatchState.FAILED
        val reason = cancellationReason
        val emergency = outcome == Outcome.HOST_DIED || outcome == Outcome.PROVIDER_DESTROYED
        return DispatchCompletionPlan(
            cancellationReason = reason,
            signalWorker = submitted && state == State.CANCELLATION_REQUESTED && !emergency,
            scheduleHardStop = submitted && state == State.CANCELLATION_REQUESTED && !emergency,
            immediateHardStop = submitted && emergency,
            finishCancellationWithoutWorker = !submitted && state == State.CANCELLATION_REQUESTED,
        )
    }

    @Synchronized
    fun hasReservedWorkerDispatch(): Boolean = dispatchState == DispatchState.RESERVED

    @Synchronized
    fun requiresImmediateWorkerHardStop(): Boolean =
        dispatchState != DispatchState.RESERVED &&
            (outcome == Outcome.HOST_DIED || outcome == Outcome.PROVIDER_DESTROYED)

    @Synchronized
    fun tryTerminal(candidate: Outcome): Boolean {
        if (state == State.TERMINAL || state == State.CLEANED) return false
        if (state == State.CANCELLATION_REQUESTED && candidate !in CANCELLATION_TERMINALS) return false
        outcome = candidate
        state = State.TERMINAL
        return true
    }

    /** Returns true exactly once when a pending cancellation grace may escalate to hard stop. */
    @Synchronized
    fun cancellationGraceExpired(): Boolean {
        if (state != State.CANCELLATION_REQUESTED || !hardStopRequired || hardStopIssued) return false
        hardStopIssued = true
        return true
    }

    @Synchronized
    fun markCleaned(): Boolean {
        if (state != State.TERMINAL) return false
        state = State.CLEANED
        return true
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(state, cancellationReason, outcome, hardStopIssued, dispatchState)

    private companion object {
        val CANCELLATION_TERMINALS = setOf(
            Outcome.CANCELLED,
            Outcome.HOST_DIED,
            Outcome.PROVIDER_DESTROYED,
        )
    }
}
