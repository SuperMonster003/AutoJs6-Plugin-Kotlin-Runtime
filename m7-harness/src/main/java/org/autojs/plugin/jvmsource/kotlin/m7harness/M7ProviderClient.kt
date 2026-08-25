package org.autojs.plugin.jvmsource.kotlin.m7harness

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.IJvmSourceCallback
import org.autojs.plugin.jvmsource.api.IJvmSourceProvider
import org.autojs.plugin.jvmsource.api.IJvmSourceSession
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCancellation
import org.autojs.plugin.jvmsource.api.JvmSourceCapabilities
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceResult
import org.autojs.plugin.jvmsource.api.JvmSourceStarted
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal sealed interface M7Terminal {
    data class Completed(val result: JvmSourceResult) : M7Terminal
    data class Failed(val error: JvmSourceError) : M7Terminal
    data class Cancelled(val cancellation: JvmSourceCancellation) : M7Terminal
}

internal data class M7SessionOutcome(
    val started: JvmSourceStarted,
    val terminal: M7Terminal,
    val diagnostics: List<JvmSourceDiagnostic>,
    val stdout: String,
    val stderr: String,
    val terminalCallerPid: Int,
    val hostCallCount: Int,
)

internal data class M7RequestProfile(
    val sourceFileName: String = "Main.kt",
    val entryClassName: String = "Main",
    val timeoutMillis: Long = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
    val declaredSourceSizeBytes: Long? = null,
    val declaredSourceSha256: JvmSha256? = null,
    val allowedHostCalls: List<String> = emptyList(),
    val grantedCapabilities: List<JvmScriptCapability> = emptyList(),
    val launchResult: Boolean = false,
)

/**
 * A Protocol 1.1 client that runs inside AutoJs6's instrumentation process. Binder therefore sees
 * the real host UID; no production caller check is disabled or bypassed by this harness.
 */
internal class M7ProviderClient(private val context: Context) : Closeable {
    private val providerRef = AtomicReference<IJvmSourceProvider?>()
    private val connectionFailure = AtomicReference<Throwable?>()
    private val connected = CountDownLatch(1)
    private val closed = AtomicBoolean(false)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (name != PROVIDER_COMPONENT) {
                connectionFailure.set(IllegalStateException("Unexpected provider component: $name"))
            } else {
                providerRef.set(IJvmSourceProvider.Stub.asInterface(service))
            }
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            providerRef.set(null)
        }

        override fun onBindingDied(name: ComponentName) {
            providerRef.set(null)
        }

        override fun onNullBinding(name: ComponentName) {
            connectionFailure.set(IllegalStateException("Provider returned a null binding: $name"))
            connected.countDown()
        }
    }

    val capabilities: JvmSourceCapabilities
    val providerVersionName: String
    val providerVersionCode: Long
    val providerDebuggable: Boolean

    init {
        require(context.packageName == HOST_PACKAGE) {
            "Harness must execute inside the AutoJs6 target process"
        }
        require(
            context.packageManager.checkSignatures(HOST_PACKAGE, PROVIDER_PACKAGE) ==
                PackageManager.SIGNATURE_MATCH,
        ) { "Host and Kotlin provider signatures differ" }
        val bound = context.bindService(
            Intent(JvmSourceContract.SERVICE_ACTION).setComponent(PROVIDER_COMPONENT),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        require(bound) { "Unable to bind the production Kotlin provider component" }
        check(connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Provider bind timed out" }
        connectionFailure.get()?.let { throw it }
        val provider = checkNotNull(providerRef.get()) { "Provider Binder is unavailable" }
        val providerInfo = JvmSourceCodec.decodeProviderInfo(provider.getProviderInfo())
        capabilities = JvmSourceCodec.decodeCapabilities(provider.getCapabilities())
        JvmSourceValidation.validateProviderInfo(providerInfo)
        JvmSourceValidation.validateCapabilities(capabilities)
        require(providerInfo.providerId == "kotlin-jvm")
        require(capabilities.languages == listOf(JvmSourceLanguage.KOTLIN))
        require(capabilities.maxConcurrentSessions == 1)
        providerVersionName = providerInfo.providerVersionName
        providerVersionCode = providerInfo.providerVersionCode
        providerDebuggable = context.packageManager.getApplicationInfo(PROVIDER_PACKAGE, 0).flags and
            ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    fun run(source: String, profile: M7RequestProfile = M7RequestProfile()): M7SessionOutcome =
        start(source.toByteArray(Charsets.UTF_8), profile).use { active -> active.awaitTerminal() }

    fun start(sourceBytes: ByteArray, profile: M7RequestProfile = M7RequestProfile()): M7ActiveSession {
        check(!closed.get()) { "Provider client is closed" }
        val provider = checkNotNull(providerRef.get()) { "Provider Binder retired" }
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        val request = JvmSourceRequest(
            requestId = requestId,
            protocolVersion = JvmProtocolVersion(
                JvmSourceContract.PROTOCOL_MAJOR,
                JvmSourceContract.PROTOCOL_MINOR,
            ),
            language = JvmSourceLanguage.KOTLIN,
            sourceFileName = profile.sourceFileName,
            sourceSizeBytes = profile.declaredSourceSizeBytes ?: sourceBytes.size.toLong(),
            sourceSha256 = profile.declaredSourceSha256 ?: JvmSha256.digest(sourceBytes),
            expectedToolchainFingerprint = capabilities.toolchainFingerprint,
            entryClassName = profile.entryClassName,
            minApi = JvmSourceContract.MIN_ANDROID_API,
            timeoutMillis = profile.timeoutMillis,
            maxStdoutBytes = capabilities.maxStdoutBytes,
            maxStderrBytes = capabilities.maxStderrBytes,
            diagnosticByteLimit = capabilities.maxDiagnosticBytes,
            allowedHostCalls = profile.allowedHostCalls,
            grantedCapabilities = profile.grantedCapabilities,
        )
        val state = M7CallbackState(request)
        val bridge = M7HostBridge(requestId, profile.launchResult)
        val root = File(context.cacheDir, "m7-jvm-source-${UUID.randomUUID()}").canonicalFile
        check(root.mkdir()) { "Unable to create harness session root" }
        val source = File(root, "Main.kt").apply { writeBytes(sourceBytes) }
        val stdout = File(root, "stdout.txt").apply { check(createNewFile()) }
        val stderr = File(root, "stderr.txt").apply { check(createNewFile()) }
        val sourceFd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val outputMode = ParcelFileDescriptor.MODE_READ_WRITE or
            ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_CREATE
        val stdoutFd = ParcelFileDescriptor.open(stdout, outputMode)
        val stderrFd = ParcelFileDescriptor.open(stderr, outputMode)
        val session = try {
            provider.openSession(
                JvmSourceCodec.encodeRequest(request),
                sourceFd,
                stdoutFd,
                stderrFd,
                bridge,
                state.callback,
            )
        } catch (error: Throwable) {
            root.deleteRecursively()
            throw error
        } finally {
            sourceFd.close()
            stdoutFd.close()
            stderrFd.close()
        }
        return M7ActiveSession(request, checkNotNull(session), bridge, state, root, stdout, stderr)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) runCatching { context.unbindService(connection) }
    }

    companion object {
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.kotlin.runtime"
        const val PROVIDER_SERVICE =
            "org.autojs.plugin.jvmsource.kotlin.service.JavaSourceCompilerService"
        const val METHOD_APP_LAUNCH = "app.launch"
        val PROVIDER_COMPONENT = ComponentName(PROVIDER_PACKAGE, PROVIDER_SERVICE)
        private const val BIND_TIMEOUT_SECONDS = 15L
    }
}

internal class M7ActiveSession(
    private val request: JvmSourceRequest,
    private val session: IJvmSourceSession,
    private val bridge: M7HostBridge,
    private val state: M7CallbackState,
    private val root: File,
    private val stdoutFile: File,
    private val stderrFile: File,
) : Closeable {
    private val terminalObserved = AtomicBoolean(false)

    fun awaitHostCall(timeoutSeconds: Long = 30L): Boolean =
        bridge.callObserved.await(timeoutSeconds, TimeUnit.SECONDS)

    fun cancel() = session.cancel()

    fun awaitTerminal(extraGraceMillis: Long = TERMINAL_GRACE_MILLIS): M7SessionOutcome {
        val waitMillis = Math.addExact(request.timeoutMillis, extraGraceMillis)
        check(state.terminalLatch.await(waitMillis, TimeUnit.MILLISECONDS)) {
            "Session terminal timed out after $waitMillis ms"
        }
        state.callbackFailure.get()?.let { throw AssertionError("Provider callback validation failed", it) }
        val started = checkNotNull(state.started.get()) { "Provider omitted onStarted" }
        val terminal = checkNotNull(state.terminal.get()) { "Provider omitted a terminal payload" }
        terminalObserved.set(true)
        SystemClock.sleep(PROVIDER_IDLE_GRACE_MILLIS)
        return M7SessionOutcome(
            started = started,
            terminal = terminal,
            diagnostics = state.diagnostics.toList(),
            stdout = stdoutFile.readText(Charsets.UTF_8),
            stderr = stderrFile.readText(Charsets.UTF_8),
            terminalCallerPid = state.terminalCallerPid.get(),
            hostCallCount = bridge.callCount.get(),
        )
    }

    override fun close() {
        if (!terminalObserved.get()) runCatching { session.close() }
        SystemClock.sleep(PROVIDER_IDLE_GRACE_MILLIS)
        check(root.deleteRecursively()) { "Unable to remove harness session root: $root" }
    }

    private companion object {
        const val TERMINAL_GRACE_MILLIS = 20_000L
        const val PROVIDER_IDLE_GRACE_MILLIS = 250L
    }
}

internal class M7HostBridge(
    private val requestId: JvmRequestId,
    private val launchResult: Boolean,
) : IJvmHostBridge.Stub() {
    val callObserved = CountDownLatch(1)
    val callCount = AtomicInteger(0)

    override fun dispatch(request: ByteArray, callback: IJvmHostBridgeCallback) {
        val call = JvmSourceCodec.decodeHostCall(request)
        JvmSourceValidation.validateHostCall(call)
        require(call.requestId == requestId)
        require(call.method == M7ProviderClient.METHOD_APP_LAUNCH)
        require(PACKAGE_PAYLOAD.matches(call.payloadJson))
        callCount.incrementAndGet()
        callObserved.countDown()
        callback.onResponse(
            JvmSourceCodec.encodeHostResponse(
                JvmHostResponse(
                    requestId = requestId,
                    callId = call.callId,
                    succeeded = true,
                    payloadJson = launchResult.toString(),
                ),
            ),
        )
    }

    override fun destroy(reason: ByteArray) {
        require(reason.size <= JvmSourceContract.MAX_METADATA_BYTES)
    }

    private companion object {
        val PACKAGE_PAYLOAD = Regex(
            "\\{\\\"packageName\\\":\\\"[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+\\\"\\}",
        )
    }
}

internal class M7CallbackState(private val request: JvmSourceRequest) {
    val started = AtomicReference<JvmSourceStarted?>()
    val terminal = AtomicReference<M7Terminal?>()
    val callbackFailure = AtomicReference<Throwable?>()
    val diagnostics = CopyOnWriteArrayList<JvmSourceDiagnostic>()
    val terminalLatch = CountDownLatch(1)
    val terminalCallerPid = AtomicInteger(0)

    val callback = object : IJvmSourceCallback.Stub() {
        override fun onStarted(metadata: ByteArray) = record {
            val value = JvmSourceCodec.decodeStarted(metadata)
            JvmSourceValidation.validateStarted(value)
            require(value.requestId == request.requestId)
            require(started.compareAndSet(null, value)) { "Duplicate onStarted" }
        }

        override fun onDiagnostic(diagnostic: ByteArray) = record {
            val value = JvmSourceCodec.decodeDiagnostic(diagnostic)
            JvmSourceValidation.validateDiagnostic(value)
            require(value.requestId == request.requestId)
            diagnostics += value
        }

        override fun onCompleted(result: ByteArray) = recordTerminal {
            val value = JvmSourceCodec.decodeResult(result)
            JvmSourceValidation.validateResult(value)
            require(value.requestId == request.requestId)
            M7Terminal.Completed(value)
        }

        override fun onFailed(error: ByteArray) = recordTerminal {
            val value = JvmSourceCodec.decodeError(error)
            JvmSourceValidation.validateError(value)
            require(value.requestId == request.requestId)
            M7Terminal.Failed(value)
        }

        override fun onCancelled(cancellation: ByteArray) = recordTerminal {
            val value = JvmSourceCodec.decodeCancellation(cancellation)
            JvmSourceValidation.validateCancellation(value)
            require(value.requestId == request.requestId)
            M7Terminal.Cancelled(value)
        }
    }

    private fun record(action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            callbackFailure.compareAndSet(null, error)
        }
    }

    private fun recordTerminal(decode: () -> M7Terminal) {
        try {
            val value = decode()
            require(terminal.compareAndSet(null, value)) { "Duplicate terminal callback" }
            terminalCallerPid.set(Binder.getCallingPid())
        } catch (error: Throwable) {
            callbackFailure.compareAndSet(null, error)
        } finally {
            terminalLatch.countDown()
        }
    }
}
