package org.autojs.plugin.jvmsource.kotlin.worker

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.kotlin.ProviderProcessIdentity
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class JavaExecutionWorkerService : Service() {
    private lateinit var identity: ProviderProcessIdentity
    private val active = AtomicReference<JavaWorkerTask?>()
    private val compilerCaller = AtomicReference<CompilerCaller?>()
    private val executionLifecycle = SingleUseWorkerLifecycle()
    private val retirementPolicy = WorkerProcessRetirementPolicy()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jvm-source-java-execution").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        identity = ProviderProcessIdentity.current(this, ":worker")
        WorkerDexLoader.clearStalePrivateDex(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        executionLifecycle.retire()
        active.get()?.cancel(JvmCancellationReason.SESSION_CLOSED)
        executor.shutdownNow()
        super.onDestroy()
        retireProcess(WorkerProcessRetirementPolicy.Trigger.PROVIDER_DESTROYED)
    }

    private val binder = object : IJavaExecutionWorker.Stub() {
        override fun execute(
            requestMetadata: ByteArray?,
            generation: Long,
            compilationElapsedMillis: Long,
            classArtifactSizeBytes: Long,
            classArtifactSha256: ByteArray?,
            dexArtifactSizeBytes: Long,
            dexArtifactSha256: ByteArray?,
            expectedClassDescriptors: Array<out String>?,
            dexFd: ParcelFileDescriptor?,
            stdoutFd: ParcelFileDescriptor?,
            stderrFd: ParcelFileDescriptor?,
            hostBridge: IJvmHostBridge?,
            callback: IJavaExecutionCallback?,
        ) {
            val admittedCompiler = enforceCompilerCaller(
                true,
                dexFd,
                stdoutFd,
                stderrFd,
            )
            if (executionLifecycle.tryClaim() != SingleUseWorkerLifecycle.Admission.ACQUIRED) {
                closeDescriptors(dexFd, stdoutFd, stderrFd)
                retireProcess(WorkerProcessRetirementPolicy.Trigger.INVALID_ADMISSION)
                return
            }
            if (requestMetadata == null || classArtifactSha256 == null || dexArtifactSha256 == null ||
                expectedClassDescriptors == null || dexFd == null || stdoutFd == null || stderrFd == null ||
                hostBridge == null || callback == null || generation <= 0L || compilationElapsedMillis < 0L
            ) {
                closeDescriptors(dexFd, stdoutFd, stderrFd)
                retireProcess(WorkerProcessRetirementPolicy.Trigger.INVALID_ADMISSION)
                return
            }
            val request = try {
                JvmSourceCodec.decodeRequest(requestMetadata.copyOf()).also(JvmSourceValidation::validateRequest)
            } catch (_: Throwable) {
                closeDescriptors(dexFd, stdoutFd, stderrFd)
                retireProcess(WorkerProcessRetirementPolicy.Trigger.INVALID_ADMISSION)
                return
            }
            val task = try {
                JavaWorkerTask(
                    context = applicationContext,
                    request = request,
                    generation = generation,
                    compilationElapsedMillis = compilationElapsedMillis,
                    classArtifactSizeBytes = classArtifactSizeBytes,
                    classArtifactSha256 = JvmSha256.fromBytes(classArtifactSha256),
                    dexArtifactSizeBytes = dexArtifactSizeBytes,
                    dexArtifactSha256 = JvmSha256.fromBytes(dexArtifactSha256),
                    expectedClassDescriptors = expectedClassDescriptors.toSet(),
                    dexFd = dexFd,
                    stdoutFd = stdoutFd,
                    stderrFd = stderrFd,
                    hostBridge = hostBridge,
                    callback = callback,
                    identity = identity,
                    expectedCompilerPid = admittedCompiler.pid,
                    expectedCompilerUid = admittedCompiler.uid,
                    onFinished = {
                        retireProcess(WorkerProcessRetirementPolicy.Trigger.TASK_FINISHED)
                    },
                    onHostDeath = {
                        retireProcess(WorkerProcessRetirementPolicy.Trigger.CALLBACK_DIED)
                    },
                )
            } catch (_: Throwable) {
                closeDescriptors(dexFd, stdoutFd, stderrFd)
                retireProcess(WorkerProcessRetirementPolicy.Trigger.INVALID_ADMISSION)
                return
            }
            if (!active.compareAndSet(null, task)) {
                closeDescriptors(dexFd, stdoutFd, stderrFd)
                sendBusy(callback, generation, request.requestId)
                retireProcess(WorkerProcessRetirementPolicy.Trigger.INVALID_ADMISSION)
                return
            }
            try {
                callback.onReady(identity.pid, generation)
                executor.execute(task)
            } catch (_: Throwable) {
                sendBusy(callback, generation, request.requestId)
                task.abortBeforeRun()
                retireProcess(WorkerProcessRetirementPolicy.Trigger.INVALID_ADMISSION)
            }
        }

        override fun cancel(requestId: ByteArray?, generation: Long, reasonWireCode: Int) {
            enforceCompilerCaller(pinIfAbsent = false)
            val task = active.get() ?: return
            if (generation != task.generation || requestId == null ||
                !requestId.contentEquals(task.request.requestId.toByteArray())
            ) return
            val reason = JvmCancellationReason.entries.firstOrNull { it.wireCode == reasonWireCode }
                ?: JvmCancellationReason.REQUESTED
            task.cancel(reason)
        }

        override fun terminate(requestId: ByteArray?, generation: Long) {
            enforceCompilerCaller(pinIfAbsent = false)
            val task = active.get() ?: return
            if (generation != task.generation || requestId == null ||
                !requestId.contentEquals(task.request.requestId.toByteArray())
            ) return
            task.cancel(JvmCancellationReason.SESSION_CLOSED)
            retireProcess(WorkerProcessRetirementPolicy.Trigger.HARD_TERMINATE)
        }
    }

    private fun enforceCompilerCaller(
        pinIfAbsent: Boolean,
        vararg descriptors: ParcelFileDescriptor?,
    ): CompilerCaller {
        val caller = CompilerCaller(Binder.getCallingPid(), Binder.getCallingUid())
        if (caller.uid != Process.myUid() || caller.pid <= 0 || caller.pid == Process.myPid()) {
            closeDescriptors(*descriptors)
            throw SecurityException("Worker accepts only its provider compiler process")
        }
        val pinned = compilerCaller.get()
        if (pinned == null && pinIfAbsent) {
            if (!compilerCaller.compareAndSet(null, caller) && compilerCaller.get() != caller) {
                closeDescriptors(*descriptors)
                throw SecurityException("Worker compiler identity changed during admission")
            }
        } else if (pinned != caller) {
            closeDescriptors(*descriptors)
            throw SecurityException("Worker command does not belong to its compiler process")
        }
        return caller
    }

    private fun sendBusy(
        callback: IJavaExecutionCallback,
        generation: Long,
        requestId: JvmRequestId,
    ) {
        val payload = JvmSourceCodec.encodeError(
            JvmSourceError(
                requestId,
                JvmSourceErrorCode.BUSY,
                JvmSourceFailurePhase.WORKER_START,
                "Worker already owns an execution",
                retryable = true,
            ),
        )
        runCatching { callback.onFailed(generation, payload) }
    }

    private fun closeDescriptors(vararg descriptors: ParcelFileDescriptor?) {
        descriptors.forEach { runCatching { it?.close() } }
    }

    private fun retireProcess(trigger: WorkerProcessRetirementPolicy.Trigger) {
        executionLifecycle.markTerminal()
        check(retirementPolicy.action(trigger) == WorkerProcessRetirementPolicy.Action.KILL_CURRENT_PROCESS)
        Process.killProcess(Process.myPid())
    }

    private data class CompilerCaller(val pid: Int, val uid: Int)

}
