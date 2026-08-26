package org.autojs.plugin.jvmsource.kotlin.worker

import android.os.Binder
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.JvmAppApi
import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmConsoleApi
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.api.JvmToastPayload
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class RemoteJvmScriptContext(
    private val request: JvmSourceRequest,
    private val bridge: IJvmHostBridge,
    private val workerCancellation: WorkerCancellation,
    private val expectedCompilerPid: Int,
    private val expectedCompilerUid: Int,
    private val stdout: PrintStream,
    private val stderr: PrintStream,
) : JvmScriptContext {
    private val callIds = AtomicLong(0L)
    private val appApi = object : JvmAppApi {
        override fun launch(packageName: String): Boolean {
            return callHost(
                capability = JvmScriptCapability.APP_LAUNCH,
                method = METHOD_APP_LAUNCH,
                validatePayload = {
                    require(PACKAGE_NAME.matches(packageName)) {
                        "Package name is outside the R1 profile"
                    }
                    "{\"packageName\":\"$packageName\"}"
                },
                validateResponse = { response ->
                    if (!response.succeeded) {
                        throw IllegalStateException(response.errorMessage ?: "Host rejected app.launch")
                    }
                    when (response.payloadJson) {
                        "true" -> true
                        "false" -> false
                        else -> throw IllegalStateException(
                            "Host returned a non-boolean app.launch response",
                        )
                    }
                },
            )
        }
    }
    private val consoleApi = object : JvmConsoleApi {
        override fun log(message: String) {
            workerCancellation.throwIfCancellationRequested()
            requireCapability(JvmScriptCapability.CONSOLE_STREAM)
            stdout.println(message)
        }

        override fun error(message: String) {
            workerCancellation.throwIfCancellationRequested()
            requireCapability(JvmScriptCapability.CONSOLE_STREAM)
            stderr.println(message)
        }
    }

    override fun app(): JvmAppApi = appApi

    override fun console(): JvmConsoleApi = consoleApi

    override fun cancellation(): JvmCancellation = workerCancellation

    override fun sleep(millis: Long) {
        requireCapability(JvmScriptCapability.SLEEP)
        require(millis >= 0L) { "Sleep duration must not be negative" }
        workerCancellation.throwIfCancellationRequested()
        try {
            Thread.sleep(millis)
        } catch (error: InterruptedException) {
            workerCancellation.throwIfCancellationRequested()
            Thread.currentThread().interrupt()
            throw error
        }
        workerCancellation.throwIfCancellationRequested()
    }

    override fun toast(message: String) {
        callHost(
            capability = JvmScriptCapability.TOAST,
            method = METHOD_TOAST_SHOW,
            validatePayload = { JvmToastPayload.encode(message) },
            validateResponse = { response ->
                if (!response.succeeded || response.payloadJson != "true") {
                    throw IllegalStateException(response.errorMessage ?: "Host rejected toast.show")
                }
            },
        )
    }

    private fun <Result> callHost(
        capability: JvmScriptCapability,
        method: String,
        validatePayload: () -> String,
        validateResponse: (JvmHostResponse) -> Result,
    ): Result = HostCallPipeline.execute(
        authorize = {
            workerCancellation.throwIfCancellationRequested()
            requireCapability(capability)
            require(method in request.allowedHostCalls) { "$method is not allowed" }
        },
        validatePayload = validatePayload,
        dispatch = { payloadJson -> dispatch(method, payloadJson) },
        validateResponse = validateResponse,
    )

    private fun dispatch(method: String, payloadJson: String): JvmHostResponse {
        // Defense in depth: the fixed pipeline checks this in its authorization stage too.
        require(method in request.allowedHostCalls) { "$method is not allowed" }
        val call = JvmHostCall(
            requestId = request.requestId,
            callId = callIds.incrementAndGet(),
            method = method,
            payloadJson = payloadJson,
            timeoutMillis = minOf(HOST_CALL_TIMEOUT_MILLIS, request.timeoutMillis),
        )
        return await(call)
    }

    private fun requireCapability(capability: JvmScriptCapability) {
        require(capability in request.grantedCapabilities) {
            "${capability.wireName} is not granted"
        }
    }

    private fun await(call: JvmHostCall): JvmHostResponse {
        val response = AtomicReference<JvmHostResponse?>()
        val failure = AtomicReference<Throwable?>()
        val delivered = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val callback = object : IJvmHostBridgeCallback.Stub() {
            override fun onResponse(encoded: ByteArray?) {
                if (!delivered.compareAndSet(false, true)) return
                try {
                    require(Binder.getCallingPid() == expectedCompilerPid) {
                        "Host response bypassed the compiler proxy"
                    }
                    require(Binder.getCallingUid() == expectedCompilerUid) {
                        "Host response came from an unexpected provider identity"
                    }
                    require(encoded != null)
                    response.set(
                        JvmSourceCodec.decodeHostResponse(encoded).also { decoded ->
                            JvmSourceValidation.validateHostResponse(decoded)
                            require(decoded.requestId == call.requestId && decoded.callId == call.callId)
                        },
                    )
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    latch.countDown()
                }
            }
        }
        bridge.dispatch(JvmSourceCodec.encodeHostCall(call), callback)
        val completed = try {
            latch.await(call.timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            workerCancellation.throwIfCancellationRequested()
            throw error
        }
        workerCancellation.throwIfCancellationRequested()
        if (!completed) throw IllegalStateException("Host call timed out")
        failure.get()?.let { throw IllegalStateException("Host response is invalid", it) }
        return checkNotNull(response.get()) { "Host response is missing" }
    }

    private companion object {
        const val METHOD_APP_LAUNCH = "app.launch"
        const val METHOD_TOAST_SHOW = "toast.show"
        const val HOST_CALL_TIMEOUT_MILLIS = 10_000L
        val PACKAGE_NAME = Regex(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+",
        )
    }
}
