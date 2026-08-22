package org.autojs.plugin.jvmsource.kotlin

import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal enum class CompilationCacheOperationFailure {
    REJECTED,
    TIMED_OUT,
    INTERRUPTED,
    FAILED,
    POISONED,
    CLOSED,
}

internal sealed interface CompilationCacheOperationResult<out T> {
    data class Success<T>(val value: T) : CompilationCacheOperationResult<T>
    data class Unavailable(
        val failure: CompilationCacheOperationFailure,
        val cause: Throwable? = null,
    ) : CompilationCacheOperationResult<Nothing>
}

/**
 * One permanent daemon thread and one admitted operation, with no pending-operation queue.
 *
 * A timed-out or caller-interrupted operation may be stuck in uninterruptible platform I/O. The
 * lane is therefore permanently poisoned instead of replacing the thread. The stuck operation
 * retains the sole slot, and every later caller receives an immediate unavailable result. This
 * bounds request retirement without accumulating tasks or cache-operation threads.
 */
internal class CompilationCacheOperationLane internal constructor(
    timeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
    threadName: String = "jvm-source-compilation-cache",
) : Closeable {
    private val timeoutMillis = timeoutMillis.also { require(it > 0L) }
    private val workerName = threadName.also { require(it.isNotBlank()) }
    private val admissionLock = ReentrantLock()
    private val signal = Semaphore(0)
    private val occupied = AtomicBoolean(false)
    private val poisoned = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private val pending = AtomicReference<FutureTask<*>?>(null)
    private val active = AtomicReference<FutureTask<*>?>(null)

    private val worker = Thread(::runWorker, workerName).apply {
        isDaemon = true
        start()
    }

    fun <T> execute(operation: () -> T): CompilationCacheOperationResult<T> {
        val task = FutureTask<T> {
            try {
                operation()
            } finally {
                // Release before FutureTask publishes completion so a sequential caller cannot
                // observe a spurious busy result between two completed cache operations.
                occupied.set(false)
            }
        }
        admissionLock.withLock {
            if (closed.get()) return unavailable(CompilationCacheOperationFailure.CLOSED)
            if (poisoned.get()) return unavailable(CompilationCacheOperationFailure.POISONED)
            if (!occupied.compareAndSet(false, true)) {
                return unavailable(CompilationCacheOperationFailure.REJECTED)
            }
            check(pending.compareAndSet(null, task)) {
                "Compilation cache lane admitted more than one operation"
            }
            signal.release()
        }

        return try {
            CompilationCacheOperationResult.Success(task.get(timeoutMillis, TimeUnit.MILLISECONDS))
        } catch (_: TimeoutException) {
            poisonAndCancel(task)
            unavailable(CompilationCacheOperationFailure.TIMED_OUT)
        } catch (_: InterruptedException) {
            poisonAndCancel(task)
            Thread.currentThread().interrupt()
            unavailable(CompilationCacheOperationFailure.INTERRUPTED)
        } catch (_: CancellationException) {
            unavailable(terminalFailure())
        } catch (error: ExecutionException) {
            CompilationCacheOperationResult.Unavailable(
                if (closed.get() || poisoned.get()) terminalFailure()
                else CompilationCacheOperationFailure.FAILED,
                error.cause ?: error,
            )
        }
    }

    override fun close() {
        admissionLock.withLock {
            if (!closed.compareAndSet(false, true)) return
            poisoned.set(true)
            pending.getAndSet(null)?.cancel(true)
            active.get()?.cancel(true)
            signal.release()
        }
        worker.interrupt()
    }

    internal fun isPoisonedForTest(): Boolean = poisoned.get()
    internal fun hasPendingForTest(): Boolean = pending.get() != null
    internal fun workerForTest(): Thread = worker

    private fun runWorker() {
        while (!closed.get() && !poisoned.get()) {
            try {
                signal.acquire()
            } catch (_: InterruptedException) {
                if (closed.get() || poisoned.get()) return
                continue
            }
            if (closed.get() || poisoned.get()) return
            val task = admissionLock.withLock {
                if (closed.get() || poisoned.get()) return
                pending.getAndSet(null)?.also { admitted -> active.set(admitted) }
            } ?: continue
            try {
                task.run()
            } finally {
                active.compareAndSet(task, null)
            }
        }
    }

    private fun poisonAndCancel(task: FutureTask<*>) {
        admissionLock.withLock {
            poisoned.set(true)
            pending.compareAndSet(task, null)
            task.cancel(true)
        }
        worker.interrupt()
    }

    private fun terminalFailure(): CompilationCacheOperationFailure =
        if (closed.get()) CompilationCacheOperationFailure.CLOSED
        else CompilationCacheOperationFailure.POISONED

    private fun unavailable(failure: CompilationCacheOperationFailure) =
        CompilationCacheOperationResult.Unavailable(failure)

    companion object {
        internal const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 1_000L
    }
}
