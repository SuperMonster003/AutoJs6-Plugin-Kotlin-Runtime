package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class WorkerCancellation : JvmCancellation {
    private val cancelled = AtomicBoolean(false)
    private val executionThread = AtomicReference<Thread?>()
    private val reason = AtomicReference<JvmCancellationReason?>()

    fun attach(thread: Thread) {
        check(executionThread.compareAndSet(null, thread))
        if (cancelled.get()) thread.interrupt()
    }

    fun cancel(reason: JvmCancellationReason) {
        this.reason.compareAndSet(null, reason)
        cancelled.set(true)
        executionThread.get()?.interrupt()
    }

    fun reason(): JvmCancellationReason = reason.get() ?: JvmCancellationReason.REQUESTED

    override fun isCancellationRequested(): Boolean = cancelled.get() ||
        executionThread.get()?.isInterrupted == true

    override fun throwIfCancellationRequested() {
        if (isCancellationRequested()) throw JvmCancellationException()
    }
}
