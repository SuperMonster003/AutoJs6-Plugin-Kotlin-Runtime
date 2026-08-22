package org.autojs.plugin.jvmsource.kotlin.worker

import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCancellation
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceResult
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservationCollector
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservationRegistry
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservedPhase
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservedProcess
import org.autojs.plugin.jvmsource.kotlin.JavaProviderResourceProbe
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservationCodec
import org.autojs.plugin.jvmsource.kotlin.JavaRuntimeDiagnosticPolicy
import org.autojs.plugin.jvmsource.kotlin.ProviderProcessIdentity
import org.autojs.plugin.jvmsource.kotlin.WorkerDexLoaderKind
import org.autojs.plugin.jvmsource.kotlin.worker.IJavaExecutionCallback
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class JavaWorkerTask(
    private val context: android.content.Context,
    val request: JvmSourceRequest,
    val generation: Long,
    private val compilationElapsedMillis: Long,
    private val classArtifactSizeBytes: Long,
    private val classArtifactSha256: JvmSha256,
    private val dexArtifactSizeBytes: Long,
    private val dexArtifactSha256: JvmSha256,
    private val expectedClassDescriptors: Set<String>,
    private val dexFd: ParcelFileDescriptor,
    private val stdoutFd: ParcelFileDescriptor,
    private val stderrFd: ParcelFileDescriptor,
    private val hostBridge: IJvmHostBridge,
    private val callback: IJavaExecutionCallback,
    private val identity: ProviderProcessIdentity,
    private val expectedCompilerPid: Int,
    private val expectedCompilerUid: Int,
    private val onFinished: () -> Unit,
    private val onHostDeath: () -> Unit,
) : Runnable {
    private val cancellation = WorkerCancellation()
    private val terminal = AtomicBoolean(false)
    private val callbackDeathLinked = AtomicBoolean(false)
    private val observationPublished = AtomicBoolean(false)
    private val thread = AtomicReference<Thread?>()
    private val completion = WorkerCompletionSignal(onFinished)
    private val callbackBinder = callback.asBinder()
    private val callbackDeathRecipient = IBinder.DeathRecipient {
        cancellation.cancel(JvmCancellationReason.HOST_DIED)
        // Direct hard-kill path: user DEX may ignore interruption and never reach finally.
        onHostDeath()
        completion.notifyOnce()
    }
    private val startedAtMillis = SystemClock.elapsedRealtime()
    private val observation = JavaProviderObservationCollector(
        JavaProviderObservationRegistry.workerStartProfile(),
    )

    override fun run() {
        thread.set(Thread.currentThread())
        cancellation.attach(Thread.currentThread())
        if (!linkCallbackDeath()) {
            val terminationStartedAt = SystemClock.elapsedRealtime()
            closeDescriptors()
            onHostDeath()
            recordTerminationAndPublish(terminationStartedAt, temporaryStorageBytesAfterCleanup = null)
            completion.notifyOnce()
            return
        }
        var loadedDex: LoadedDex? = null
        var stdout: DigestingBoundedOutputStream? = null
        var stderr: DigestingBoundedOutputStream? = null
        var stdoutPrint: PrintStream? = null
        var stderrPrint: PrintStream? = null
        val originalOut = System.out
        val originalErr = System.err
        var systemStreamsReplaced = false
        fun closeOutputChannels() {
            restoreSystemStreams(originalOut, originalErr, systemStreamsReplaced)
            systemStreamsReplaced = false
            runCatching { stdoutPrint?.flush() }
            runCatching { stderrPrint?.flush() }
            runCatching { stdoutPrint?.close() }
            runCatching { stderrPrint?.close() }
            runCatching { stdout?.close() }
            runCatching { stderr?.close() }
        }
        try {
            ensureActive()
            stdout = DigestingBoundedOutputStream(
                ParcelFileDescriptor.AutoCloseOutputStream(stdoutFd),
                request.maxStdoutBytes,
            )
            stderr = DigestingBoundedOutputStream(
                ParcelFileDescriptor.AutoCloseOutputStream(stderrFd),
                request.maxStderrBytes,
            )
            stdoutPrint = PrintStream(stdout, true, Charsets.UTF_8.name())
            stderrPrint = PrintStream(stderr, true, Charsets.UTF_8.name())
            System.setOut(stdoutPrint)
            System.setErr(stderrPrint)
            systemStreamsReplaced = true

            val executionStartedAt = SystemClock.elapsedRealtime()
            val loadStartedAt = SystemClock.elapsedRealtime()
            val dexLoader = WorkerDexLoader(context)
            val validatedDex = dexLoader.validateStructure(
                descriptor = dexFd,
                expectedSizeBytes = dexArtifactSizeBytes,
                expectedSha256 = dexArtifactSha256,
                expectedClassDescriptors = expectedClassDescriptors,
                requestMinApi = request.minApi,
                ensureActive = ::ensureActive,
            )
            ensureActive()
            val activeLoadedDex = dexLoader.createClassLoader(
                validated = validatedDex,
                generation = generation,
                requestId = request.requestId.toString(),
                parent = AutoJsJvmEntry::class.java.classLoader!!,
            )
            loadedDex = activeLoadedDex
            val loadedTemporaryStorageBytes = when (activeLoadedDex.actualLoaderKind) {
                WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER -> 0L
                // API 24/25 also has runtime-generated optimized output. Until a bounded tree
                // measurement is retained, unknown is safer than reporting only classes.dex.
                WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER -> null
            }
            ensureActive()
            val loadedEntry = WorkerEntryFactory.loadFromArt(
                activeLoadedDex.classLoader,
                request.entryClassName,
            )
            ensureActive()
            val entry = WorkerEntryFactory.instantiate(loadedEntry)
            observation.recordDuration(JavaProviderObservedPhase.LOAD, elapsedSince(loadStartedAt))
            observation.recordResource(
                JavaProviderResourceProbe.capture(
                    process = JavaProviderObservedProcess.WORKER,
                    phase = JavaProviderObservedPhase.LOAD,
                    temporaryStorageBytes = loadedTemporaryStorageBytes,
                    outputBytes = 0L,
                ),
            )
            val runStartedAt = SystemClock.elapsedRealtime()
            val resultValue = try {
                entry.run(
                    RemoteJvmScriptContext(
                        request,
                        hostBridge,
                        cancellation,
                        expectedCompilerPid,
                        expectedCompilerUid,
                        checkNotNull(stdoutPrint),
                        checkNotNull(stderrPrint),
                    ),
                )
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
            ensureActive()
            val resultJson = WorkerJsonValue.encode(resultValue)
            ensureActive()
            observation.recordDuration(JavaProviderObservedPhase.RUN, elapsedSince(runStartedAt))

            closeOutputChannels()
            val capturedStdout = checkNotNull(stdout)
            val capturedStderr = checkNotNull(stderr)
            if (capturedStdout.overflowed || capturedStderr.overflowed) {
                throw JavaProviderFailure(
                    JvmSourceErrorCode.OUTPUT_LIMIT_EXCEEDED,
                    JvmSourceFailurePhase.EXECUTION,
                    "Program output exceeded its negotiated limit",
                )
            }
            val stdoutSnapshot = capturedStdout.snapshot()
            val stderrSnapshot = capturedStderr.snapshot()
            observation.recordResource(
                JavaProviderResourceProbe.capture(
                    process = JavaProviderObservedProcess.WORKER,
                    phase = JavaProviderObservedPhase.RUN,
                    temporaryStorageBytes = loadedTemporaryStorageBytes,
                    outputBytes = runCatching {
                        Math.addExact(stdoutSnapshot.sizeBytes, stderrSnapshot.sizeBytes)
                    }.getOrNull(),
                ),
            )
            val executionElapsed = elapsedSince(executionStartedAt)
            val totalElapsed = Math.addExact(compilationElapsedMillis, executionElapsed)
            val loadedArtifact = activeLoadedDex.validatedArtifact
            check(validatedDex.stage == WorkerDexLoadStage.STRUCTURE_VALIDATED)
            check(activeLoadedDex.stage == WorkerDexLoadStage.ART_CLASS_LOADER_CREATED)
            check(loadedEntry.stage == WorkerDexLoadStage.ART_ENTRY_CLASS_LOADED)
            check(loadedArtifact.loaderKind == activeLoadedDex.actualLoaderKind)
            check(loadedArtifact.requestMinApi == request.minApi)
            check(loadedArtifact.deviceApi == Build.VERSION.SDK_INT)
            finishCompleted(
                JvmSourceResult(
                    requestId = request.requestId,
                    toolchainFingerprint = request.expectedToolchainFingerprint,
                    classArtifactSizeBytes = classArtifactSizeBytes,
                    classArtifactSha256 = classArtifactSha256,
                    dexArtifactSizeBytes = dexArtifactSizeBytes,
                    dexArtifactSha256 = dexArtifactSha256,
                    dexVersion = loadedArtifact.version,
                    loaderKind = loadedArtifact.loaderKind.apiKind,
                    resultJson = resultJson,
                    stdoutSizeBytes = stdoutSnapshot.sizeBytes,
                    stdoutSha256 = stdoutSnapshot.sha256,
                    stderrSizeBytes = stderrSnapshot.sizeBytes,
                    stderrSha256 = stderrSnapshot.sha256,
                    workerPid = identity.pid,
                    workerUid = identity.uid,
                    workerProcessName = identity.processName,
                    workerGeneration = generation,
                    internalBridgeCallerPid = 0,
                    internalBridgeCallerUid = 0,
                    internalBridgeCallCount = 0,
                    compilationElapsedMillis = compilationElapsedMillis,
                    executionElapsedMillis = executionElapsed,
                    elapsedMillis = totalElapsed,
                ),
            )
        } catch (_: JvmCancellationException) {
            closeOutputChannels()
            finishCancelled(cancellation.reason())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            closeOutputChannels()
            finishCancelled(cancellation.reason())
        } catch (error: JavaProviderFailure) {
            closeOutputChannels()
            finishFailed(error.code, error.phase)
        } catch (error: Throwable) {
            closeOutputChannels()
            JavaRuntimeDiagnosticPolicy.sourceLine(
                error,
                request.entryClassName,
                request.sourceFileName,
            )?.let { line ->
                runCatching { callback.onRuntimeDiagnostic(generation, line) }
            }
            finishFailed(
                if (error is ClassNotFoundException || error is LinkageError) {
                    JvmSourceErrorCode.CLASS_LOADING_FAILED
                } else {
                    JvmSourceErrorCode.EXECUTION_FAILED
                },
                JvmSourceFailurePhase.EXECUTION,
            )
        } finally {
            val terminationStartedAt = SystemClock.elapsedRealtime()
            closeOutputChannels()
            val temporaryStorageBytesAfterCleanup = loadedDex?.let { activeLoadedDex ->
                runCatching {
                    if (activeLoadedDex.closeAndVerifyTemporaryStorageReleased()) 0L else null
                }.getOrNull()
            }
            closeDescriptors()
            unlinkCallbackDeath()
            thread.set(null)
            recordTerminationAndPublish(terminationStartedAt, temporaryStorageBytesAfterCleanup)
            completion.notifyOnce()
        }
    }

    fun cancel(reason: JvmCancellationReason) = cancellation.cancel(reason)

    fun abortBeforeRun() {
        val terminationStartedAt = SystemClock.elapsedRealtime()
        cancellation.cancel(JvmCancellationReason.SESSION_CLOSED)
        closeDescriptors()
        unlinkCallbackDeath()
        recordTerminationAndPublish(terminationStartedAt, temporaryStorageBytesAfterCleanup = null)
        completion.notifyOnce()
    }

    private fun recordTerminationAndPublish(
        terminationStartedAt: Long,
        temporaryStorageBytesAfterCleanup: Long?,
    ) {
        observation.recordDuration(
            JavaProviderObservedPhase.TERMINATION,
            elapsedSince(terminationStartedAt),
        )
        observation.recordResource(
            JavaProviderResourceProbe.capture(
                process = JavaProviderObservedProcess.WORKER,
                phase = JavaProviderObservedPhase.TERMINATION,
                temporaryStorageBytes = temporaryStorageBytesAfterCleanup,
                outputBytes = null,
            ),
        )
        if (observationPublished.compareAndSet(false, true)) {
            val snapshot = observation.snapshot()
            JavaProviderObservationRegistry.publishWorker(snapshot)
            runCatching {
                callback.onObservation(generation, JavaProviderObservationCodec.encode(snapshot))
            }
        }
    }

    private fun finishCompleted(result: JvmSourceResult) {
        JvmSourceValidation.validateResultAgainstRuntime(result, request, Build.VERSION.SDK_INT)
        val encoded = JvmSourceCodec.encodeResult(result)
        if (!terminal.compareAndSet(false, true)) return
        callback.onCompleted(generation, encoded)
    }

    private fun finishFailed(code: JvmSourceErrorCode, phase: JvmSourceFailurePhase) {
        val error = JvmSourceError(
            requestId = request.requestId,
            code = code,
            phase = phase,
            message = publicMessage(code),
            retryable = false,
        )
        val encoded = JvmSourceCodec.encodeError(error)
        if (!terminal.compareAndSet(false, true)) return
        callback.onFailed(generation, encoded)
    }

    private fun finishCancelled(reason: JvmCancellationReason) {
        val encoded = JvmSourceCodec.encodeCancellation(
            JvmSourceCancellation(
                requestId = request.requestId,
                reason = reason,
                phase = JvmSourceFailurePhase.EXECUTION,
                elapsedMillis = elapsedSince(startedAtMillis),
            ),
        )
        if (!terminal.compareAndSet(false, true)) return
        callback.onCancelled(generation, encoded)
    }

    private fun ensureActive() {
        cancellation.throwIfCancellationRequested()
    }

    private fun linkCallbackDeath(): Boolean {
        if (!callbackDeathLinked.compareAndSet(false, true)) return callbackBinder.isBinderAlive
        return try {
            callbackBinder.linkToDeath(callbackDeathRecipient, 0)
            callbackBinder.isBinderAlive
        } catch (_: RemoteException) {
            callbackDeathLinked.set(false)
            cancellation.cancel(JvmCancellationReason.HOST_DIED)
            false
        }
    }

    private fun unlinkCallbackDeath() {
        if (callbackDeathLinked.compareAndSet(true, false)) {
            runCatching { callbackBinder.unlinkToDeath(callbackDeathRecipient, 0) }
        }
    }

    private fun closeDescriptors() {
        runCatching { dexFd.close() }
        runCatching { stdoutFd.close() }
        runCatching { stderrFd.close() }
    }

    private fun restoreSystemStreams(originalOut: PrintStream, originalErr: PrintStream, replaced: Boolean) {
        if (replaced) {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }

    private fun elapsedSince(start: Long): Long =
        (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)

    private fun publicMessage(code: JvmSourceErrorCode): String = when (code) {
        JvmSourceErrorCode.ARTIFACT_INVALID -> "Generated DEX failed integrity validation"
        JvmSourceErrorCode.ENTRY_POINT_MISSING -> "Java entry point is missing"
        JvmSourceErrorCode.ENTRY_POINT_AMBIGUOUS -> "More than one entry point was produced"
        JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE -> "Java entry is incompatible with the entry API"
        JvmSourceErrorCode.CLASS_LOADING_FAILED -> "Java entry could not be loaded"
        JvmSourceErrorCode.OUTPUT_LIMIT_EXCEEDED -> "Program output exceeded its limit"
        else -> "Kotlin source execution failed"
    }
}

internal class WorkerCompletionSignal(private val action: () -> Unit) {
    private val delivered = AtomicBoolean(false)

    fun notifyOnce() {
        if (delivered.compareAndSet(false, true)) action()
    }
}
