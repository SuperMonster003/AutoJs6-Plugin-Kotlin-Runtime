package org.autojs.plugin.jvmsource.kotlin.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmSourceCallback
import org.autojs.plugin.jvmsource.api.IJvmSourceSession
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.autojs.plugin.jvmsource.api.JvmIsolationCapability
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCancellation
import org.autojs.plugin.jvmsource.api.JvmSourceCapabilities
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceResult
import org.autojs.plugin.jvmsource.api.JvmSourceStarted
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.kotlin.D8JavaCompiler
import org.autojs.plugin.jvmsource.kotlin.CompilationArtifactCacheKey
import org.autojs.plugin.jvmsource.kotlin.CompilationArtifactProvenance
import org.autojs.plugin.jvmsource.kotlin.CompilationCacheLookup
import org.autojs.plugin.jvmsource.kotlin.CompilationCacheLookupOutcome
import org.autojs.plugin.jvmsource.kotlin.CompilationCacheMissReason
import org.autojs.plugin.jvmsource.kotlin.CompilationCacheOperationFailure
import org.autojs.plugin.jvmsource.kotlin.CompilationCacheOperationResult
import org.autojs.plugin.jvmsource.kotlin.CompilationCacheRequestObservation
import org.autojs.plugin.jvmsource.kotlin.EncodedDiagnosticBudget
import org.autojs.plugin.jvmsource.kotlin.JavaProviderEnvironment
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import org.autojs.plugin.jvmsource.kotlin.JavaProviderCacheMissReason
import org.autojs.plugin.jvmsource.kotlin.JavaProviderCacheOutcome
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservation
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservationCodec
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservationCollector
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservationRegistry
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservedPhase
import org.autojs.plugin.jvmsource.kotlin.JavaProviderObservedProcess
import org.autojs.plugin.jvmsource.kotlin.JavaProviderResourceProbe
import org.autojs.plugin.jvmsource.kotlin.JavaProviderRuntime
import org.autojs.plugin.jvmsource.kotlin.KotlinDiagnosticSanitizer
import org.autojs.plugin.jvmsource.kotlin.KotlinJvmCompiler
import org.autojs.plugin.jvmsource.kotlin.KotlinSourcePolicy
import org.autojs.plugin.jvmsource.kotlin.RawKotlinDiagnostic
import org.autojs.plugin.jvmsource.kotlin.PrivateSessionWorkspace
import org.autojs.plugin.jvmsource.kotlin.ProviderInstalledIdentityDecision
import org.autojs.plugin.jvmsource.kotlin.ProviderInstalledIdentityPolicy
import org.autojs.plugin.jvmsource.kotlin.ProviderFileIdentity
import org.autojs.plugin.jvmsource.kotlin.ProviderDigests
import org.autojs.plugin.jvmsource.kotlin.ProviderProcessIdentity
import org.autojs.plugin.jvmsource.kotlin.UserClassJarSummary
import org.autojs.plugin.jvmsource.kotlin.UserClassJarWriter
import org.autojs.plugin.jvmsource.kotlin.BuildConfig
import org.autojs.plugin.jvmsource.kotlin.BoundedSourceIngress
import org.autojs.plugin.jvmsource.kotlin.worker.IJavaExecutionCallback
import org.autojs.plugin.jvmsource.kotlin.worker.IJavaExecutionWorker
import org.autojs.plugin.jvmsource.kotlin.worker.JavaExecutionWorkerService
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class RemoteJavaSourceSession(
    private val context: Context,
    private val ownerUid: Int,
    private val request: JvmSourceRequest,
    private val descriptors: OwnedSessionDescriptors,
    private val hostBridge: IJvmHostBridge,
    private val callback: IJvmSourceCallback,
    private val capabilities: JvmSourceCapabilities,
    private val environment: JavaProviderEnvironment,
    private val compilerIdentity: ProviderProcessIdentity,
    private val callerVerifier: HostCallerVerifier,
    private val compilerExecutor: ExecutorService,
    private val scheduler: ScheduledExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteJavaSourceSession) -> Unit,
) : IJvmSourceSession.Stub() {
    private val generation = nextGeneration.incrementAndGet()
    private val termination = JavaSessionTerminationPolicy()
    private val compilerShutdownPolicy = CompilerServiceShutdownPolicy()
    private val cleaned = AtomicBoolean(false)
    private val resourceCleanup = ProviderResourceCleanupBarrier()
    private val started = AtomicBoolean(false)
    private val handedToWorker = AtomicBoolean(false)
    private val workerBinding = WorkerServiceBindingLifecycle()
    private val callbackDeathLinked = AtomicBoolean(false)
    private val bridgeDeathLinked = AtomicBoolean(false)
    private val workerDeathLinked = AtomicBoolean(false)
    private val workerDeathObserved = AtomicBoolean(false)
    private val workerHardStopIssued = AtomicBoolean(false)
    private val compilerKillScheduled = AtomicBoolean(false)
    private val reservedDispatchKillScheduled = AtomicBoolean(false)
    private val workerRetirementWatchdogScheduled = AtomicBoolean(false)
    private val compilerThread = AtomicReference<Thread?>()
    private val worker = AtomicReference<IJavaExecutionWorker?>()
    private val workerBinder = AtomicReference<IBinder?>()
    private val workerObservation = AtomicReference<WorkerBinderObservation?>()
    private val cancellationReason = AtomicReference<JvmCancellationReason?>()
    private val cacheObservation = AtomicReference<CompilationCacheRequestObservation?>()
    private val workerPerformanceObservation = AtomicReference<JavaProviderObservation?>()
    private val runtimeDiagnosticLine = AtomicReference<Int?>()
    private val terminalDelivery = ProviderTerminalDeliveryBarrier<() -> Unit>(termination)
    private val createdAtMillis = SystemClock.elapsedRealtime()
    private val cleanupStartedAtMillis = AtomicLong(0L)
    private val compilerTemporaryStorageBytesAfterCleanup = AtomicReference<Long?>(null)
    private val performanceObservationPublished = AtomicBoolean(false)
    private val performanceObservation = JavaProviderObservationCollector(
        JavaProviderObservationRegistry.compilerStartProfile(environmentWasAlreadyInitialized = true),
    )
    private val diagnosticBytesEmitted = AtomicInteger(0)
    private val callbackBinder = callback.asBinder()
    private val bridgeBinder = hostBridge.asBinder()
    private val callbackDeathRecipient = IBinder.DeathRecipient(::hostEndpointDied)
    private val bridgeDeathRecipient = IBinder.DeathRecipient(::hostEndpointDied)
    private val workerDeathRecipient = IBinder.DeathRecipient(::workerDied)

    @Volatile
    private var timeoutFuture: ScheduledFuture<*>? = null

    @Volatile
    private var workspace: PrivateSessionWorkspace? = null

    @Volatile
    private var artifacts: CompiledArtifacts? = null

    private val hostProxy = SessionHostBridgeProxy(
        ownerUid = ownerUid,
        request = request,
        externalBridge = hostBridge,
        workerObservation = workerObservation::get,
        onViolation = ::workerProtocolViolation,
    )

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        requestCancellation(JvmCancellationReason.REQUESTED)
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        requestCancellation(JvmCancellationReason.SESSION_CLOSED)
    }

    fun start() {
        if (!linkHostDeaths()) {
            cleanup()
            return
        }
        timeoutFuture = scheduler.schedule(
            { requestCancellation(JvmCancellationReason.TIMEOUT) },
            request.timeoutMillis,
            TimeUnit.MILLISECONDS,
        )
        try {
            compilerExecutor.execute {
                compilerThread.set(Thread.currentThread())
                try {
                    runCompilation()
                } finally {
                    compilerThread.compareAndSet(Thread.currentThread(), null)
                    if (termination.snapshot().terminal) cleanup()
                }
            }
        } catch (_: RejectedExecutionException) {
            finishError(JvmSourceErrorCode.INTERNAL, JvmSourceFailurePhase.COMPILATION)
        }
    }

    fun rejectBusy() {
        if (!linkHostDeaths()) {
            cleanup()
            return
        }
        emitStarted()
        finishError(JvmSourceErrorCode.BUSY, JvmSourceFailurePhase.NEGOTIATION, retryable = true)
    }

    fun serviceDestroyed() {
        val plan = termination.requestCancellation(
            JvmCancellationReason.SESSION_CLOSED,
            workerLive = hasLiveWorker(),
        )
        cancellationReason.compareAndSet(null, plan.reason)
        if (terminalDelivery.commitSilent(JavaSessionTerminationPolicy.Outcome.PROVIDER_DESTROYED)) {
            compilerThread.get()?.interrupt()
        }
        if (termination.requiresImmediateWorkerHardStop()) {
            hardStopLiveWorker(JvmCancellationReason.SESSION_CLOSED)
        }
        cleanup()
    }

    fun hasCriticalWorkForServiceShutdown(): Boolean {
        val dispatch = termination.snapshot().dispatchState
        return compilerThread.get() != null || dispatch == JavaSessionTerminationPolicy.DispatchState.RESERVED ||
            (dispatch == JavaSessionTerminationPolicy.DispatchState.SUBMITTED && !workerDeathObserved.get() &&
                !cleaned.get())
    }

    private fun runCompilation() {
        try {
            ensureActive()
            emitStarted()
            validateProviderProfile()
            environment.compilerClasspath.verifyInstalled()
            ensureActive()

            val privateWorkspace = PrivateSessionWorkspace.create(context, request.sourceFileName)
                .also { workspace = it }
            val normalizedSourceSha256 = copyAndValidateSource(privateWorkspace)
            ensureActive()

            val cacheKey = compilationCacheKey(normalizedSourceSha256)
            val compilationCache = environment.compilationCache
            val cacheLane = environment.compilationCacheOperationLane
            var cachePublishEligible = false
            if (compilationCache == null || cacheLane == null) {
                cacheObservation.set(
                    CompilationCacheRequestObservation(
                        CompilationCacheLookupOutcome.MISS,
                        CompilationCacheMissReason.CACHE_DISABLED,
                    ),
                )
                environment.compilationCacheTelemetry.recordMiss(CompilationCacheMissReason.CACHE_DISABLED)
            } else {
                val lookupAttempt = cacheLane.execute {
                    val currentIdentity = runCatching(environment::resolveCurrentInstalledIdentity).getOrNull()
                    when (val identity = ProviderInstalledIdentityPolicy.evaluate(
                        environment.installedIdentity,
                        currentIdentity,
                    )) {
                        ProviderInstalledIdentityDecision.MATCH -> IdentityCheckedCacheLookup(
                            identity,
                            compilationCache.lookupObserved(
                                cacheKey,
                                request.minApi,
                                Build.VERSION.SDK_INT,
                                request.entryClassName,
                            ),
                        )
                        ProviderInstalledIdentityDecision.UNAVAILABLE,
                        ProviderInstalledIdentityDecision.DRIFTED ->
                            IdentityCheckedCacheLookup(identity, lookup = null)
                    }
                }
                // If request cancellation interrupted the caller while awaiting the cache lane,
                // terminal state wins rather than silently falling through to kotlinc/D8.
                ensureActive()
                val identityCheckedLookup = (lookupAttempt as? CompilationCacheOperationResult.Success)?.value
                val lookup = identityCheckedLookup?.lookup
                val cached = lookup?.artifacts
                var cacheMissReason = when (identityCheckedLookup?.identity) {
                    ProviderInstalledIdentityDecision.UNAVAILABLE ->
                        CompilationCacheMissReason.PROVIDER_IDENTITY_UNAVAILABLE
                    ProviderInstalledIdentityDecision.DRIFTED ->
                        CompilationCacheMissReason.PROVIDER_IDENTITY_DRIFTED
                    ProviderInstalledIdentityDecision.MATCH ->
                        lookup?.missReason ?: CompilationCacheMissReason.CACHE_UNAVAILABLE
                    null -> CompilationCacheMissReason.CACHE_UNAVAILABLE
                }
                cachePublishEligible = identityCheckedLookup?.identity == ProviderInstalledIdentityDecision.MATCH &&
                    lookup != null
                if (cached != null) {
                    ensureActive()
                    val materializationAttempt = cacheLane.execute {
                        compilationCache.materialize(
                            cached = cached,
                            destinationProgramJar = java.io.File(privateWorkspace.cacheHitDirectory, "program.jar"),
                            destinationDexFile = java.io.File(privateWorkspace.cacheHitDirectory, "classes.dex"),
                            requestMinApi = request.minApi,
                            deviceApi = Build.VERSION.SDK_INT,
                            entryClassName = request.entryClassName,
                            ensureActive = ::ensureActive,
                        )
                    }
                    ensureActive()
                    val materialized =
                        (materializationAttempt as? CompilationCacheOperationResult.Success)?.value
                    if (materialized != null) {
                        cacheObservation.set(
                            CompilationCacheRequestObservation(CompilationCacheLookupOutcome.HIT, missReason = null),
                        )
                        environment.compilationCacheTelemetry.recordHit()
                        artifacts = CompiledArtifacts(
                            dexFile = materialized.dexFile,
                            programJar = materialized.programJar,
                            classSummary = materialized.classSummary,
                            classIdentity = materialized.classIdentity,
                            dexIdentity = materialized.dexIdentity,
                            compilationElapsedMillis = elapsedMillis(),
                            cacheKey = materialized.cacheKey,
                            publishOnSuccess = false,
                        )
                        recordCompilerResource(
                            JavaProviderObservedPhase.COMPILE,
                            materialized.classIdentity.sizeBytes,
                            materialized.dexIdentity.sizeBytes,
                        )
                        bindWorker()
                        return
                    }
                    if (materializationAttempt is CompilationCacheOperationResult.Unavailable &&
                        materializationAttempt.failure == CompilationCacheOperationFailure.FAILED
                    ) {
                        // Only an ordinary verification/copy failure proves this hit unusable.
                        // Timeout/rejection leaves the entry untouched and poisons or occupies the
                        // lane; either case safely degrades to compilation without publishing.
                        val invalidation = cacheLane.execute { compilationCache.invalidate(cacheKey) }
                        ensureActive()
                        cachePublishEligible = invalidation is CompilationCacheOperationResult.Success
                        cacheMissReason = CompilationCacheMissReason.MATERIALIZATION_FAILED
                    } else {
                        cachePublishEligible = false
                        cacheMissReason = CompilationCacheMissReason.CACHE_UNAVAILABLE
                    }
                }
                cacheObservation.set(
                    CompilationCacheRequestObservation(CompilationCacheLookupOutcome.MISS, cacheMissReason),
                )
                environment.compilationCacheTelemetry.recordMiss(cacheMissReason)
            }

            val compileStartedAt = SystemClock.elapsedRealtime()
            val compilation = try {
                KotlinJvmCompiler(environment.compilerClasspath).compile(
                    sourceFile = privateWorkspace.sourceFile,
                    outputDirectory = privateWorkspace.classesDirectory,
                    ensureActive = ::ensureActive,
                )
            } finally {
                performanceObservation.recordDuration(
                    JavaProviderObservedPhase.COMPILE,
                    elapsedSince(compileStartedAt),
                )
            }
            emitCompilerDiagnostics(compilation.diagnostics, privateWorkspace)
            if (!compilation.succeeded) {
                throw JavaProviderFailure(
                    JvmSourceErrorCode.COMPILATION_FAILED,
                    JvmSourceFailurePhase.COMPILATION,
                    "Kotlin/JVM compiler rejected the Kotlin compilation unit",
                )
            }
            ensureActive()

            val classSummary = UserClassJarWriter.write(
                privateWorkspace.classesDirectory,
                privateWorkspace.programJar,
                request.entryClassName,
            )
            val classIdentity = ProviderDigests.file(
                privateWorkspace.programJar,
                JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES,
            )
            environment.compilerClasspath.verifyInstalled()
            val d8StartedAt = SystemClock.elapsedRealtime()
            val dexFile = try {
                D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                    privateWorkspace.programJar,
                    privateWorkspace.d8OutputDirectory,
                    request.minApi,
                    ::ensureActive,
                )
            } finally {
                performanceObservation.recordDuration(
                    JavaProviderObservedPhase.D8,
                    elapsedSince(d8StartedAt),
                )
            }
            val dexIdentity = ProviderDigests.file(dexFile, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
            if (!dexFile.setReadOnly()) {
                throw JavaProviderFailure(
                    JvmSourceErrorCode.ARTIFACT_INVALID,
                    JvmSourceFailurePhase.DEXING,
                    "Unable to freeze classes.dex",
                )
            }
            ensureActive()
            artifacts = CompiledArtifacts(
                dexFile = dexFile,
                programJar = privateWorkspace.programJar,
                classSummary = classSummary,
                classIdentity = classIdentity,
                dexIdentity = dexIdentity,
                compilationElapsedMillis = elapsedMillis(),
                cacheKey = cacheKey,
                publishOnSuccess = cachePublishEligible,
            )
            recordCompilerResource(
                JavaProviderObservedPhase.D8,
                classIdentity.sizeBytes,
                dexIdentity.sizeBytes,
            )
            bindWorker()
        } catch (_: SessionStopped) {
            Unit
        } catch (error: JavaProviderFailure) {
            finishError(error.code, error.phase, publicMessageOverride = error.publicMessage)
        } catch (_: Throwable) {
            if (!termination.snapshot().terminal) {
                finishError(JvmSourceErrorCode.INTERNAL, JvmSourceFailurePhase.COMPILATION)
            }
        }
    }

    private fun validateProviderProfile() {
        val entrySimpleName = request.entryClassName.substringAfterLast('.')
        if (request.language != JvmSourceLanguage.KOTLIN ||
            request.sourceFileName != "$entrySimpleName.kt" ||
            request.minApi != JvmSourceContract.MIN_ANDROID_API ||
            request.allowedHostCalls.any { it !in SUPPORTED_HOST_METHODS } ||
            request.grantedCapabilities.any { it !in capabilities.scriptCapabilities }
        ) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.INVALID_REQUEST,
                JvmSourceFailurePhase.NEGOTIATION,
                "Request is outside the Kotlin Protocol 1.1 profile",
            )
        }
        if (!capabilities.isolationCapabilities.containsAll(JvmIsolationCapability.entries)) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.INTERNAL,
                JvmSourceFailurePhase.NEGOTIATION,
                "Provider isolation configuration is incomplete",
            )
        }
    }

    private fun copyAndValidateSource(privateWorkspace: PrivateSessionWorkspace): JvmSha256 {
        val sourceBytes = try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptors.source).use { input ->
                val bytes = BoundedSourceIngress.read(
                    input = input,
                    declaredSizeBytes = request.sourceSizeBytes,
                    maximumSizeBytes = capabilities.maxSourceBytes,
                    ensureActive = ::ensureActive,
                )
                descriptors.source.checkError()
                bytes
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.INVALID_REQUEST,
                JvmSourceFailurePhase.INPUT,
                "Unable to read the Kotlin source",
                error,
            )
        }
        if (sourceBytes.size.toLong() != request.sourceSizeBytes ||
            JvmSha256.digest(sourceBytes) != request.sourceSha256
        ) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.ARTIFACT_INVALID,
                JvmSourceFailurePhase.INPUT,
                "Source framing or SHA-256 differs from request metadata",
            )
        }
        val source = KotlinSourcePolicy.decodeAndValidate(
            bytes = sourceBytes,
            sourceFileName = request.sourceFileName,
            entryClassName = request.entryClassName,
        )
        val normalizedBytes = source.toByteArray(Charsets.UTF_8)
        FileOutputStream(privateWorkspace.sourceFile).use { output ->
            output.write(normalizedBytes)
            output.fd.sync()
        }
        return JvmSha256.digest(normalizedBytes)
    }

    private fun compilationCacheKey(normalizedSourceSha256: JvmSha256): CompilationArtifactCacheKey {
        val installed = environment.installedIdentity
        return CompilationArtifactCacheKey.compute(
            CompilationArtifactProvenance(
                rawSourceSha256 = request.sourceSha256,
                normalizedSourceSha256 = normalizedSourceSha256,
                sourceCharsetPolicy = KotlinSourcePolicy.CACHE_CHARSET_POLICY,
                sourceNormalizationPolicy = KotlinSourcePolicy.CACHE_NORMALIZATION_POLICY,
                sourceFileName = request.sourceFileName,
                entryClassName = request.entryClassName,
                language = request.language.wireName,
                protocolMajor = request.protocolVersion.major,
                protocolMinor = request.protocolVersion.minor,
                entryApiVersion = JvmSourceContract.ENTRY_API_VERSION,
                sourceCompilerFamily = capabilities.sourceCompilerFamily.wireName,
                sourceCompilerVersion = capabilities.sourceCompilerVersion,
                sourceCompilerOptions = KotlinJvmCompiler.CACHE_OPTIONS_IDENTITY,
                d8Version = capabilities.d8Version,
                d8Options = D8JavaCompiler.optionsIdentity(request.minApi),
                minApi = request.minApi,
                allowedHostCalls = request.allowedHostCalls,
                runtimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
                toolchainFingerprint = capabilities.toolchainFingerprint,
                providerId = JavaProviderRuntime.PROVIDER_ID,
                providerApiVersionName = JavaProviderRuntime.PROVIDER_VERSION_NAME,
                providerApiVersionCode = JavaProviderRuntime.PROVIDER_VERSION_CODE,
                providerPackageName = installed.packageName,
                providerComponentName = installed.componentName,
                providerUid = installed.uid,
                providerSignerSha256 = installed.signerSha256,
                installedVersionCode = installed.versionCode,
                installedVersionName = installed.versionName,
                installedLastUpdateTime = installed.lastUpdateTime,
                runtimeApi = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                vmArchitecture = System.getProperty("os.arch").orEmpty(),
            ),
        )
    }

    private fun emitCompilerDiagnostics(
        values: List<RawKotlinDiagnostic>,
        privateWorkspace: PrivateSessionWorkspace,
    ) {
        val privateFiles = listOf(
            privateWorkspace.classesDirectory,
            privateWorkspace.programJar,
            privateWorkspace.d8OutputDirectory,
            environment.compilerClasspath.androidJar,
            environment.compilerClasspath.entryApiJar,
            environment.compilerClasspath.kotlinStdlibJar,
        )
        values.forEach { value ->
            val sanitized = KotlinDiagnosticSanitizer.sanitize(
                value = value,
                byteLimit = request.diagnosticByteLimit,
                privateFiles = privateFiles,
                sourceFile = privateWorkspace.sourceFile,
            )
            emitDiagnostic(
                JvmSourceDiagnostic(
                    requestId = request.requestId,
                    severity = sanitized.severity,
                    code = sanitized.code,
                    message = sanitized.message,
                    sourceFileName = request.sourceFileName.takeIf { sanitized.line != null },
                    line = sanitized.line,
                    column = sanitized.column,
                ),
            )
        }
    }

    private fun emitDiagnostic(diagnostic: JvmSourceDiagnostic) {
        val remaining = request.diagnosticByteLimit - diagnosticBytesEmitted.get()
        val encoded = EncodedDiagnosticBudget.encodeWithin(diagnostic, remaining) ?: return
        diagnosticBytesEmitted.addAndGet(encoded.size)
        dispatchCallback { callback.onDiagnostic(encoded) }
    }

    private fun bindWorker() {
        check(workerBinding.beginBinding())
        handedToWorker.set(true)
        val intent = Intent(context, JavaExecutionWorkerService::class.java)
        val bound = try {
            context.bindService(intent, workerConnection, Context.BIND_AUTO_CREATE)
        } catch (error: Throwable) {
            workerBinding.bindingReturned(bound = false)
            handedToWorker.set(false)
            throw JavaProviderFailure(
                JvmSourceErrorCode.WORKER_DIED,
                JvmSourceFailurePhase.WORKER_START,
                "Unable to bind the Kotlin execution worker",
                error,
            )
        }
        if (workerBinding.bindingReturned(bound) == WorkerServiceBindingLifecycle.Action.UNBIND_NOW) {
            performWorkerUnbind()
        }
        if (!bound) {
            handedToWorker.set(false)
            throw JavaProviderFailure(
                JvmSourceErrorCode.WORKER_DIED,
                JvmSourceFailurePhase.WORKER_START,
                "Unable to bind the Kotlin execution worker",
            )
        }
    }

    private val workerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (termination.snapshot().terminal) {
                cleanup()
                return
            }
            val expected = ComponentName(context, JavaExecutionWorkerService::class.java)
            if (name != expected) {
                finishError(JvmSourceErrorCode.WORKER_DIED, JvmSourceFailurePhase.WORKER_START)
                return
            }
            val remote = IJavaExecutionWorker.Stub.asInterface(service)
            worker.set(remote)
            workerBinder.set(service)
            workerDeathObserved.set(false)
            try {
                service.linkToDeath(workerDeathRecipient, 0)
                workerDeathLinked.set(true)
                if (!termination.canDispatchWorkerArtifacts()) {
                    hardStopLiveWorker(cancellationReason.get() ?: JvmCancellationReason.SESSION_CLOSED)
                    if (termination.snapshot().terminal) cleanup()
                    return
                }
                handArtifactsToWorker(remote)
            } catch (_: Throwable) {
                finishError(JvmSourceErrorCode.WORKER_DIED, JvmSourceFailurePhase.WORKER_START)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = workerDied()

        override fun onBindingDied(name: ComponentName) = workerDied()

        override fun onNullBinding(name: ComponentName) {
            finishError(JvmSourceErrorCode.WORKER_DIED, JvmSourceFailurePhase.WORKER_START)
        }
    }

    private fun handArtifactsToWorker(remote: IJavaExecutionWorker) {
        val compiled = checkNotNull(artifacts)
        var dex: ParcelFileDescriptor? = null
        var stdout: ParcelFileDescriptor? = null
        var stderr: ParcelFileDescriptor? = null
        var dispatchReserved = false
        var submitted = false
        try {
            dex = ParcelFileDescriptor.open(compiled.dexFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val outputs = descriptors.duplicateOutputsForWorker()
            stdout = outputs.first
            stderr = outputs.second
            if (!termination.reserveWorkerDispatch()) {
                hardStopLiveWorker(cancellationReason.get() ?: JvmCancellationReason.SESSION_CLOSED)
                return
            }
            dispatchReserved = true
            remote.execute(
                JvmSourceCodec.encodeRequest(request),
                generation,
                compiled.compilationElapsedMillis,
                compiled.classIdentity.sizeBytes,
                compiled.classIdentity.sha256.toByteArray(),
                compiled.dexIdentity.sizeBytes,
                compiled.dexIdentity.sha256.toByteArray(),
                compiled.classSummary.dexDescriptors.toTypedArray(),
                dex,
                stdout,
                stderr,
                hostProxy,
                internalCallback,
            )
            submitted = true
        } finally {
            if (dispatchReserved) {
                handleWorkerDispatchCompletion(termination.completeWorkerDispatch(submitted))
            }
            runCatching { dex?.close() }
            runCatching { stdout?.close() }
            runCatching { stderr?.close() }
        }
    }

    private fun handleWorkerDispatchCompletion(plan: JavaSessionTerminationPolicy.DispatchCompletionPlan) {
        val reason = plan.cancellationReason ?: cancellationReason.get() ?: JvmCancellationReason.SESSION_CLOSED
        when {
            plan.immediateHardStop -> hardStopLiveWorker(reason)
            plan.signalWorker && plan.scheduleHardStop -> beginWorkerCancellationGrace(reason)
            plan.finishCancellationWithoutWorker -> finishCancellation(reason)
        }
        if (termination.snapshot().terminal) cleanup()
    }

    private val internalCallback = object : IJavaExecutionCallback.Stub() {
        override fun onReady(pid: Int, callbackGeneration: Long) {
            val observed = observeInternalCaller(callbackGeneration)
                ?: throw SecurityException("Worker readiness identity is invalid")
            if (pid != observed.pid || !workerObservation.compareAndSet(null, observed)) {
                workerProtocolViolation()
                throw SecurityException("Worker readiness identity changed")
            }
        }

        override fun onCompleted(callbackGeneration: Long, encodedResult: ByteArray?) {
            val observed = requireObservedTerminalCaller(callbackGeneration) ?: return
            if (runtimeDiagnosticLine.get() != null) return workerProtocolViolation()
            val compiled = artifacts ?: return workerProtocolViolation()
            val runtimeApi = Build.VERSION.SDK_INT
            val expectedRuntimeLoader = JvmDexRuntimeProfile.loaderKindForApi(runtimeApi)
                ?: return workerProtocolViolation()
            val workerResult = try {
                require(encodedResult != null)
                JvmSourceCodec.decodeResult(encodedResult).also {
                    JvmSourceValidation.validateResultAgainstRuntime(it, request, runtimeApi)
                }
            } catch (_: Throwable) {
                return workerProtocolViolation()
            }
            val expectedProcess = context.packageName + ":worker"
            if (workerResult.workerPid != observed.pid || workerResult.workerUid != observed.uid ||
                workerResult.workerProcessName != expectedProcess ||
                workerResult.workerGeneration != generation ||
                workerResult.classArtifactSizeBytes != compiled.classIdentity.sizeBytes ||
                workerResult.classArtifactSha256 != compiled.classIdentity.sha256 ||
                workerResult.dexArtifactSizeBytes != compiled.dexIdentity.sizeBytes ||
                workerResult.dexArtifactSha256 != compiled.dexIdentity.sha256 ||
                workerResult.dexVersion !in JvmDexRuntimeProfile.admittedVersions(runtimeApi) ||
                workerResult.loaderKind != expectedRuntimeLoader ||
                workerResult.compilationElapsedMillis != compiled.compilationElapsedMillis
            ) {
                return workerProtocolViolation()
            }
            val bridge = try {
                hostProxy.snapshot()
            } catch (_: Throwable) {
                return workerProtocolViolation()
            }
            val verified = workerResult.copy(
                classArtifactSizeBytes = compiled.classIdentity.sizeBytes,
                classArtifactSha256 = compiled.classIdentity.sha256,
                dexArtifactSizeBytes = compiled.dexIdentity.sizeBytes,
                dexArtifactSha256 = compiled.dexIdentity.sha256,
                workerPid = observed.pid,
                workerUid = observed.uid,
                workerProcessName = expectedProcess,
                workerGeneration = generation,
                internalBridgeCallerPid = bridge.callerPid,
                internalBridgeCallerUid = bridge.callerUid,
                internalBridgeCallCount = bridge.callCount,
            )
            try {
                JvmSourceValidation.validateResultAgainstRuntime(verified, request, runtimeApi)
            } catch (_: Throwable) {
                return workerProtocolViolation()
            }
            finishCompleted(verified)
        }

        override fun onFailed(callbackGeneration: Long, encodedError: ByteArray?) {
            requireObservedTerminalCaller(callbackGeneration) ?: return
            val error = try {
                require(encodedError != null)
                JvmSourceCodec.decodeError(encodedError).also { require(it.requestId == request.requestId) }
            } catch (_: Throwable) {
                return workerProtocolViolation()
            }
            if (runtimeDiagnosticLine.get() != null && error.phase != JvmSourceFailurePhase.EXECUTION) {
                return workerProtocolViolation()
            }
            finishError(error.code, error.phase, retryable = error.retryable)
        }

        override fun onCancelled(callbackGeneration: Long, encodedCancellation: ByteArray?) {
            requireObservedTerminalCaller(callbackGeneration) ?: return
            if (runtimeDiagnosticLine.get() != null) return workerProtocolViolation()
            val value = try {
                require(encodedCancellation != null)
                JvmSourceCodec.decodeCancellation(encodedCancellation).also {
                    require(it.requestId == request.requestId)
                }
            } catch (_: Throwable) {
                return workerProtocolViolation()
            }
            finishCancellation(cancellationReason.get() ?: value.reason, value.phase)
        }

        override fun onObservation(callbackGeneration: Long, encodedObservation: ByteArray?) {
            requireObservedTerminalCaller(callbackGeneration) ?: return
            val value = try {
                require(encodedObservation != null)
                JavaProviderObservationCodec.decode(encodedObservation).also { observation ->
                    require(observation.startProfile == org.autojs.plugin.jvmsource.kotlin.JavaProviderStartProfile.COLD)
                    require(observation.cacheOutcome == JavaProviderCacheOutcome.NOT_EVALUATED)
                    require(observation.cacheMissReason == null)
                    require(observation.resources.all { it.process == JavaProviderObservedProcess.WORKER })
                }
            } catch (_: Throwable) {
                return workerProtocolViolation()
            }
            if (!workerPerformanceObservation.compareAndSet(null, value)) return workerProtocolViolation()
            // This registry write occurs in :compiler; the worker-local slot is intentionally not
            // relied upon because the disposable :worker process exits immediately afterwards.
            JavaProviderObservationRegistry.publishWorker(value)
        }

        override fun onRuntimeDiagnostic(callbackGeneration: Long, line: Int) {
            requireObservedTerminalCaller(callbackGeneration) ?: return
            if (line !in 1..MAX_SOURCE_POSITION || !runtimeDiagnosticLine.compareAndSet(null, line)) {
                return workerProtocolViolation()
            }
            emitDiagnostic(
                JvmSourceDiagnostic(
                    requestId = request.requestId,
                    severity = JvmDiagnosticSeverity.ERROR,
                    code = "KOTLIN_RUNTIME_EXCEPTION",
                    message = "Kotlin execution failed",
                    sourceFileName = request.sourceFileName,
                    line = line,
                    // Protocol V1 requires line and column together. Column 1 is the safe line-only
                    // baseline and is not inferred from exception text.
                    column = 1,
                ),
            )
        }
    }

    private fun observeInternalCaller(callbackGeneration: Long): WorkerBinderObservation? {
        val observed = WorkerBinderObservation(
            pid = Binder.getCallingPid(),
            uid = Binder.getCallingUid(),
            generation = callbackGeneration,
        )
        if (callbackGeneration != generation || observed.pid <= 0 || observed.pid == compilerIdentity.pid ||
            observed.uid != Process.myUid()
        ) {
            workerProtocolViolation()
            return null
        }
        return observed
    }

    private fun requireObservedTerminalCaller(callbackGeneration: Long): WorkerBinderObservation? {
        val actual = observeInternalCaller(callbackGeneration) ?: return null
        val ready = workerObservation.get()
        if (ready == null || ready != actual) {
            workerProtocolViolation()
            return null
        }
        return actual
    }

    private fun emitStarted() {
        if (!started.compareAndSet(false, true)) return
        val payload = JvmSourceCodec.encodeStarted(
            JvmSourceStarted(
                requestId = request.requestId,
                language = request.language,
                providerId = JavaProviderRuntime.PROVIDER_ID,
                toolchainFingerprint = capabilities.toolchainFingerprint,
                compilerPid = compilerIdentity.pid,
                compilerUid = compilerIdentity.uid,
                compilerProcessName = compilerIdentity.processName,
            ),
        )
        dispatchCallback { callback.onStarted(payload) }
    }

    private fun finishCompleted(result: JvmSourceResult) {
        val payload = JvmSourceCodec.encodeResult(result)
        if (!terminalDelivery.commitExternal(
                outcome = JavaSessionTerminationPolicy.Outcome.SUCCESS,
                terminal = { callback.onCompleted(payload) },
            )
        ) return
        publishSuccessfulCompilationToCache()
        timeoutFuture?.cancel(false)
        cleanup()
    }

    /** Cache publication is downstream of the exact success terminal winning every cancellation/death race. */
    private fun publishSuccessfulCompilationToCache() {
        val compiled = artifacts ?: return
        if (!compiled.publishOnSuccess) return
        val compilationCache = environment.compilationCache ?: return
        val cacheLane = environment.compilationCacheOperationLane ?: return
        val publication = cacheLane.execute {
            val currentIdentity = runCatching(environment::resolveCurrentInstalledIdentity).getOrNull()
            when (ProviderInstalledIdentityPolicy.evaluate(environment.installedIdentity, currentIdentity)) {
                ProviderInstalledIdentityDecision.MATCH -> {
                    compilationCache.publish(
                        cacheKey = compiled.cacheKey,
                        programJar = compiled.programJar,
                        dexFile = compiled.dexFile,
                        classSummary = compiled.classSummary,
                        classIdentity = compiled.classIdentity,
                        dexIdentity = compiled.dexIdentity,
                        requestMinApi = request.minApi,
                        deviceApi = Build.VERSION.SDK_INT,
                        entryClassName = request.entryClassName,
                        ensureActive = ::ensureCachePublicationActive,
                    )
                    CachePublicationOutcome.PUBLISHED
                }
                ProviderInstalledIdentityDecision.UNAVAILABLE ->
                    CachePublicationOutcome.IDENTITY_UNAVAILABLE
                ProviderInstalledIdentityDecision.DRIFTED ->
                    CachePublicationOutcome.IDENTITY_DRIFTED
            }
        }
        if (publication is CompilationCacheOperationResult.Success &&
            publication.value == CachePublicationOutcome.PUBLISHED
        ) {
            environment.compilationCacheTelemetry.recordPublication()
        } else {
            environment.compilationCacheTelemetry.recordPublicationFailure()
        }
    }

    private fun ensureCachePublicationActive() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Cache publication interrupted")
        check(termination.snapshot().outcome == JavaSessionTerminationPolicy.Outcome.SUCCESS) {
            "Compilation cache publication lost the success terminal"
        }
    }

    private fun finishError(
        code: JvmSourceErrorCode,
        phase: JvmSourceFailurePhase,
        retryable: Boolean = false,
        publicMessageOverride: String? = null,
    ) {
        emitStarted()
        val payload = JvmSourceCodec.encodeError(
            JvmSourceError(
                requestId = request.requestId,
                code = code,
                phase = phase,
                message = publicMessageOverride ?: publicMessage(code),
                retryable = retryable,
            ),
        )
        if (!terminalDelivery.commitExternal(
                outcome = terminalOutcome(code, phase),
                terminal = { callback.onFailed(payload) },
            )
        ) return
        timeoutFuture?.cancel(false)
        cleanup()
    }

    private fun finishCancellation(
        reason: JvmCancellationReason,
        phase: JvmSourceFailurePhase = currentPhase(),
    ) {
        emitStarted()
        val payload = JvmSourceCodec.encodeCancellation(
            JvmSourceCancellation(request.requestId, reason, phase, elapsedMillis()),
        )
        if (!terminalDelivery.commitExternal(
                outcome = JavaSessionTerminationPolicy.Outcome.CANCELLED,
                terminal = { callback.onCancelled(payload) },
            )
        ) return
        timeoutFuture?.cancel(false)
        cleanup()
    }

    private fun requestCancellation(reason: JvmCancellationReason) {
        val plan = termination.requestCancellation(reason, workerLive = hasLiveWorker())
        if (!plan.accepted) return
        cancellationReason.compareAndSet(null, plan.reason)
        val effectiveReason = cancellationReason.get() ?: plan.reason
        if (plan.interruptCompiler) compilerThread.get()?.interrupt()
        if (plan.waitForDispatchCompletion) {
            scheduleReservedDispatchProcessDeath()
            return
        }
        if (plan.signalWorker && plan.scheduleHardStop) {
            beginWorkerCancellationGrace(effectiveReason)
        } else {
            finishCancellation(effectiveReason)
        }
    }

    private fun beginWorkerCancellationGrace(effectiveReason: JvmCancellationReason) {
        if (cancelLiveWorker(effectiveReason)) {
            try {
                scheduler.schedule(
                    {
                        if (termination.cancellationGraceExpired()) {
                            hardStopLiveWorker(effectiveReason)
                            finishCancellation(effectiveReason, JvmSourceFailurePhase.EXECUTION)
                        }
                    },
                    WORKER_CANCEL_GRACE_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            } catch (_: RejectedExecutionException) {
                finishCancellation(effectiveReason, JvmSourceFailurePhase.EXECUTION)
            }
        } else {
            finishCancellation(effectiveReason, JvmSourceFailurePhase.EXECUTION)
        }
    }

    private fun workerProtocolViolation() {
        finishError(JvmSourceErrorCode.WORKER_DIED, JvmSourceFailurePhase.WORKER_START)
    }

    private fun workerDied() {
        workerDeathObserved.set(true)
        if (!termination.snapshot().terminal) {
            val cancelled = cancellationReason.get()
            if (cancelled != null) {
                finishCancellation(cancelled, JvmSourceFailurePhase.EXECUTION)
            } else {
                finishError(JvmSourceErrorCode.WORKER_DIED, JvmSourceFailurePhase.EXECUTION)
            }
        } else if (resourceCleanup.observeWorkerDeath()) {
            finishWorkerCleanup()
        }
    }

    private fun dispatchCallback(block: () -> Unit) {
        callbackLane.dispatch(block) { hostEndpointDied() }
    }

    private fun hostEndpointDied() {
        val plan = termination.requestCancellation(JvmCancellationReason.HOST_DIED, workerLive = hasLiveWorker())
        cancellationReason.compareAndSet(null, plan.reason)
        if (terminalDelivery.commitSilent(JavaSessionTerminationPolicy.Outcome.HOST_DIED)) {
            compilerThread.get()?.interrupt()
            if (termination.requiresImmediateWorkerHardStop()) {
                hardStopLiveWorker(JvmCancellationReason.HOST_DIED)
            }
        }
        cleanup()
    }

    private fun linkHostDeaths(): Boolean {
        try {
            callbackBinder.linkToDeath(callbackDeathRecipient, 0)
            callbackDeathLinked.set(true)
            bridgeBinder.linkToDeath(bridgeDeathRecipient, 0)
            bridgeDeathLinked.set(true)
        } catch (_: RemoteException) {
            hostEndpointDied()
            return false
        }
        if (!callbackBinder.isBinderAlive || !bridgeBinder.isBinderAlive) {
            hostEndpointDied()
            return false
        }
        return true
    }

    private fun cleanup() {
        val compiling = compilerThread.get()
        if (compiling != null) {
            compiling.interrupt()
            if (compilerShutdownPolicy.requiresSessionWatchdog(true, handedToWorker.get())) {
                scheduleCompilerProcessDeath()
            }
            return
        }
        if (termination.hasReservedWorkerDispatch()) return
        if (!resourceCleanup.beginCleanup()) return
        cleanupStartedAtMillis.compareAndSet(0L, SystemClock.elapsedRealtime())
        timeoutFuture?.cancel(false)
        hostProxy.close()
        descriptors.close()
        val privateWorkspace = workspace
        compilerTemporaryStorageBytesAfterCleanup.set(
            privateWorkspace?.let { activeWorkspace ->
                runCatching {
                    if (activeWorkspace.closeAndVerifyRemoved()) 0L else null
                }.getOrNull()
            },
        )
        workspace = null
        val waitForWorkerDeath =
            termination.snapshot().dispatchState == JavaSessionTerminationPolicy.DispatchState.SUBMITTED &&
                !workerDeathObserved.get()
        if (!resourceCleanup.resourcesClosed(waitForWorkerDeath)) {
            unlinkHostDeaths()
            scheduleWorkerRetirementWatchdog()
        } else {
            finishWorkerCleanup()
        }
    }

    private fun cancelLiveWorker(reason: JvmCancellationReason): Boolean {
        val remote = worker.get() ?: return false
        val binder = workerBinder.get() ?: return false
        if (!isSameLiveWorker(remote, binder)) return false
        return runCatching {
            remote.cancel(request.requestId.toByteArray(), generation, reason.wireCode)
            true
        }.getOrDefault(false)
    }

    private fun hasLiveWorker(): Boolean {
        val remote = worker.get() ?: return false
        val binder = workerBinder.get() ?: return false
        return isSameLiveWorker(remote, binder)
    }

    private fun hardStopLiveWorker(reason: JvmCancellationReason) {
        val remote = worker.get() ?: return
        val binder = workerBinder.get() ?: return
        if (!isSameLiveWorker(remote, binder) || !workerHardStopIssued.compareAndSet(false, true)) return
        runCatching { remote.cancel(request.requestId.toByteArray(), generation, reason.wireCode) }
        if (!isSameLiveWorker(remote, binder)) return
        runCatching { remote.terminate(request.requestId.toByteArray(), generation) }
        val observed = workerObservation.get()?.takeIf { it.generation == generation } ?: return
        if (isSameLiveWorker(remote, binder) && workerObservation.get() == observed &&
            observed.pid > 0 && observed.pid != Process.myPid()
        ) {
            Process.killProcess(observed.pid)
        }
    }

    private fun isSameLiveWorker(remote: IJavaExecutionWorker, binder: IBinder): Boolean =
        !workerDeathObserved.get() && worker.get() === remote && workerBinder.get() === binder &&
            binder.isBinderAlive && binder.pingBinder() &&
            workerObservation.get()?.generation?.let { it == generation } != false

    private fun unbindWorker() {
        if (workerBinding.requestUnbind() == WorkerServiceBindingLifecycle.Action.UNBIND_NOW) {
            performWorkerUnbind()
        }
    }

    private fun performWorkerUnbind() = runCatching { context.unbindService(workerConnection) }

    private fun unlinkHostDeaths() {
        if (callbackDeathLinked.compareAndSet(true, false)) {
            runCatching { callbackBinder.unlinkToDeath(callbackDeathRecipient, 0) }
        }
        if (bridgeDeathLinked.compareAndSet(true, false)) {
            runCatching { bridgeBinder.unlinkToDeath(bridgeDeathRecipient, 0) }
        }
    }

    private fun unlinkWorkerDeath() {
        if (workerDeathLinked.compareAndSet(true, false)) {
            workerBinder.get()?.let { binder -> runCatching { binder.unlinkToDeath(workerDeathRecipient, 0) } }
        }
    }

    private fun finishWorkerCleanup() {
        hardStopLiveWorker(cancellationReason.get() ?: JvmCancellationReason.SESSION_CLOSED)
        unbindWorker()
        unlinkHostDeaths()
        unlinkWorkerDeath()
        publishPerformanceObservation()
        val retirement = terminalDelivery.retire()
        if (!retirement.accepted) return
        cleaned.set(true)
        onFinished(this)
        retirement.externalTerminal?.let(::dispatchCallback)
    }

    private fun scheduleReservedDispatchProcessDeath() {
        if (!reservedDispatchKillScheduled.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    Thread.sleep(COMPILER_CANCEL_GRACE_MILLIS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (termination.hasReservedWorkerDispatch()) Process.killProcess(Process.myPid())
            },
            "jvm-source-reserved-dispatch-watchdog",
        ).apply {
            isDaemon = false
            start()
        }
    }

    private fun scheduleWorkerRetirementWatchdog() {
        if (!workerRetirementWatchdogScheduled.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    Thread.sleep(WORKER_RETIREMENT_ACK_TIMEOUT_MILLIS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (!cleaned.get() && !workerDeathObserved.get()) {
                    hardStopLiveWorker(cancellationReason.get() ?: JvmCancellationReason.SESSION_CLOSED)
                    try {
                        Thread.sleep(WORKER_RETIREMENT_ACK_TIMEOUT_MILLIS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    if (!cleaned.get() && !workerDeathObserved.get()) Process.killProcess(Process.myPid())
                }
            },
            "jvm-source-worker-retirement-watchdog",
        ).apply {
            isDaemon = false
            start()
        }
    }

    private fun scheduleCompilerProcessDeath() {
        if (!compilerKillScheduled.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    Thread.sleep(COMPILER_CANCEL_GRACE_MILLIS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (!cleaned.get() && compilerThread.get() != null) Process.killProcess(Process.myPid())
            },
            "jvm-source-compiler-cancel-watchdog",
        ).apply {
            isDaemon = false
            start()
        }
    }

    private fun ensureActive() {
        if (termination.snapshot().terminal || Thread.currentThread().isInterrupted) throw SessionStopped()
    }

    private fun terminalOutcome(
        code: JvmSourceErrorCode,
        phase: JvmSourceFailurePhase,
    ): JavaSessionTerminationPolicy.Outcome = when {
        code == JvmSourceErrorCode.WORKER_DIED -> JavaSessionTerminationPolicy.Outcome.WORKER_DIED
        phase == JvmSourceFailurePhase.EXECUTION -> JavaSessionTerminationPolicy.Outcome.RUNTIME_ERROR
        else -> JavaSessionTerminationPolicy.Outcome.COMPILATION_ERROR
    }

    private fun currentPhase(): JvmSourceFailurePhase = when {
        handedToWorker.get() -> JvmSourceFailurePhase.EXECUTION
        workspace != null -> JvmSourceFailurePhase.COMPILATION
        else -> JvmSourceFailurePhase.INPUT
    }

    private fun elapsedMillis(): Long =
        (SystemClock.elapsedRealtime() - createdAtMillis).coerceAtLeast(0L)

    private fun elapsedSince(startedAtMillis: Long): Long =
        (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)

    private fun recordCompilerResource(
        phase: JavaProviderObservedPhase,
        classArtifactBytes: Long,
        dexArtifactBytes: Long,
    ) {
        val temporaryBytes = runCatching {
            Math.addExact(request.sourceSizeBytes, Math.addExact(classArtifactBytes, dexArtifactBytes))
        }.getOrNull()
        performanceObservation.recordResource(
            JavaProviderResourceProbe.capture(
                process = JavaProviderObservedProcess.COMPILER,
                phase = phase,
                temporaryStorageBytes = temporaryBytes,
                outputBytes = 0L,
            ),
        )
    }

    private fun publishPerformanceObservation() {
        if (!performanceObservationPublished.compareAndSet(false, true)) return
        cleanupStartedAtMillis.get().takeIf { it > 0L }?.let { started ->
            performanceObservation.recordDuration(
                JavaProviderObservedPhase.TERMINATION,
                elapsedSince(started),
            )
        }
        cacheObservation.get()?.let { cache ->
            when (cache.outcome) {
                CompilationCacheLookupOutcome.HIT ->
                    performanceObservation.recordCache(JavaProviderCacheOutcome.HIT)
                CompilationCacheLookupOutcome.MISS -> performanceObservation.recordCache(
                    JavaProviderCacheOutcome.MISS,
                    when (checkNotNull(cache.missReason)) {
                        CompilationCacheMissReason.NOT_FOUND -> JavaProviderCacheMissReason.NOT_FOUND
                        CompilationCacheMissReason.INVALID_OR_EXPIRED ->
                            JavaProviderCacheMissReason.INVALID_OR_EXPIRED
                        CompilationCacheMissReason.CACHE_UNAVAILABLE ->
                            JavaProviderCacheMissReason.CACHE_UNAVAILABLE
                        CompilationCacheMissReason.MATERIALIZATION_FAILED ->
                            JavaProviderCacheMissReason.MATERIALIZATION_FAILED
                        CompilationCacheMissReason.CACHE_DISABLED ->
                            JavaProviderCacheMissReason.CACHE_DISABLED
                        CompilationCacheMissReason.PROVIDER_IDENTITY_UNAVAILABLE ->
                            JavaProviderCacheMissReason.PROVIDER_IDENTITY_UNAVAILABLE
                        CompilationCacheMissReason.PROVIDER_IDENTITY_DRIFTED ->
                            JavaProviderCacheMissReason.PROVIDER_IDENTITY_DRIFTED
                    },
                )
            }
        }
        performanceObservation.recordResource(
            JavaProviderResourceProbe.capture(
                process = JavaProviderObservedProcess.COMPILER,
                phase = JavaProviderObservedPhase.TERMINATION,
                temporaryStorageBytes = compilerTemporaryStorageBytesAfterCleanup.get(),
                outputBytes = null,
            ),
        )
        JavaProviderObservationRegistry.publishCompiler(performanceObservation.snapshot())
    }

    private fun publicMessage(code: JvmSourceErrorCode): String = when (code) {
        JvmSourceErrorCode.INVALID_REQUEST -> "Request is outside the Kotlin Protocol 1.1 profile"
        JvmSourceErrorCode.SOURCE_TOO_LARGE -> "Kotlin source exceeds its declared limit"
        JvmSourceErrorCode.COMPILATION_FAILED -> "Kotlin/JVM compiler could not compile the Kotlin source"
        JvmSourceErrorCode.DEXING_FAILED -> "D8 could not produce classes.dex"
        JvmSourceErrorCode.ARTIFACT_INVALID -> "A source or compiled artifact failed integrity validation"
        JvmSourceErrorCode.ENTRY_POINT_MISSING -> "Requested Kotlin entry class must implement AutoJsJvmEntry"
        JvmSourceErrorCode.ENTRY_POINT_AMBIGUOUS -> "More than one entry point was produced"
        JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE ->
            "Kotlin entry class must be public and concrete with a public no-argument constructor"
        JvmSourceErrorCode.CLASS_LOADING_FAILED -> "Kotlin entry could not be loaded"
        JvmSourceErrorCode.EXECUTION_FAILED -> "AutoJsJvmEntry execution failed"
        JvmSourceErrorCode.OUTPUT_LIMIT_EXCEEDED -> "Program output exceeded its limit"
        JvmSourceErrorCode.TIMEOUT -> "Kotlin source execution timed out"
        JvmSourceErrorCode.WORKER_DIED -> "Disposable execution worker failed"
        JvmSourceErrorCode.BUSY -> "Kotlin source provider is busy"
        JvmSourceErrorCode.HOST_CALL_REJECTED -> "A host call was rejected"
        else -> "Kotlin source provider failed"
    }

    private data class CompiledArtifacts(
        val dexFile: java.io.File,
        val programJar: java.io.File,
        val classSummary: UserClassJarSummary,
        val classIdentity: ProviderFileIdentity,
        val dexIdentity: ProviderFileIdentity,
        val compilationElapsedMillis: Long,
        val cacheKey: CompilationArtifactCacheKey,
        val publishOnSuccess: Boolean,
    )

    private data class IdentityCheckedCacheLookup(
        val identity: ProviderInstalledIdentityDecision,
        val lookup: CompilationCacheLookup?,
    )

    private enum class CachePublicationOutcome {
        PUBLISHED,
        IDENTITY_UNAVAILABLE,
        IDENTITY_DRIFTED,
    }

    private class SessionStopped : RuntimeException()

    private companion object {
        const val METHOD_APP_LAUNCH = "app.launch"
        const val METHOD_TOAST_SHOW = "toast.show"
        val SUPPORTED_HOST_METHODS = setOf(METHOD_APP_LAUNCH, METHOD_TOAST_SHOW)
        const val WORKER_CANCEL_GRACE_MILLIS = 500L
        const val WORKER_RETIREMENT_ACK_TIMEOUT_MILLIS = 500L
        const val COMPILER_CANCEL_GRACE_MILLIS = 2_000L
        const val MAX_SOURCE_POSITION = 1_000_000
        val nextGeneration = AtomicLong(1L)
    }
}
