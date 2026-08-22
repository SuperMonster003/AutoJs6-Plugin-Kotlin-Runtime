package org.autojs.plugin.jvmsource.kotlin.service

import android.os.Binder
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.api.JvmToastPayload
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal data class WorkerBinderObservation(
    val pid: Int,
    val uid: Int,
    val generation: Long,
)

internal data class InternalBridgeObservation(
    val callerPid: Int,
    val callerUid: Int,
    val callCount: Int,
)

internal class SessionHostBridgeProxy(
    private val ownerUid: Int,
    private val request: JvmSourceRequest,
    private val externalBridge: IJvmHostBridge,
    private val workerObservation: () -> WorkerBinderObservation?,
    private val onViolation: () -> Unit,
) : IJvmHostBridge.Stub() {
    private val closed = AtomicBoolean(false)
    private val seenCallIds = ConcurrentHashMap.newKeySet<Long>()
    private val caller = AtomicReference<WorkerBinderObservation?>()
    private val callCount = AtomicInteger(0)

    override fun dispatch(encodedCall: ByteArray?, workerCallback: IJvmHostBridgeCallback?) {
        val observed = verifyWorkerCaller() ?: return
        if (!pinCallerAndCount(observed)) {
            onViolation()
            return
        }
        if (encodedCall == null || workerCallback == null || closed.get()) {
            onViolation()
            return
        }
        val call = try {
            JvmSourceCodec.decodeHostCall(encodedCall).also(JvmSourceValidation::validateHostCall)
        } catch (_: Throwable) {
            onViolation()
            return
        }
        if (call.requestId != request.requestId || call.method !in request.allowedHostCalls ||
            call.method !in SUPPORTED_METHODS || !seenCallIds.add(call.callId) ||
            !validPayload(call)
        ) {
            respondFailure(workerCallback, call, "HOST_CALL_REJECTED", "Host call is outside the R1 allowlist")
            onViolation()
            return
        }
        val delivered = AtomicBoolean(false)
        val responseCallback = object : IJvmHostBridgeCallback.Stub() {
            override fun onResponse(encodedResponse: ByteArray?) {
                if (!delivered.compareAndSet(false, true)) return
                if (Binder.getCallingUid() != ownerUid || encodedResponse == null || closed.get()) {
                    onViolation()
                    return
                }
                val response = try {
                    JvmSourceCodec.decodeHostResponse(encodedResponse).also { value ->
                        JvmSourceValidation.validateHostResponse(value)
                        require(value.requestId == call.requestId && value.callId == call.callId)
                    }
                } catch (_: Throwable) {
                    onViolation()
                    return
                }
                runCatching {
                    workerCallback.onResponse(JvmSourceCodec.encodeHostResponse(response))
                }.onFailure { onViolation() }
            }
        }
        runCatching {
            externalBridge.dispatch(JvmSourceCodec.encodeHostCall(call), responseCallback)
        }.onFailure {
            respondFailure(workerCallback, call, "HOST_UNAVAILABLE", "Host bridge is unavailable")
            onViolation()
        }
    }

    override fun destroy(reason: ByteArray?) {
        verifyWorkerCaller() ?: return
        if (reason == null || reason.size > JvmSourceContract.MAX_METADATA_BYTES) onViolation()
        closed.set(true)
    }

    fun snapshot(): InternalBridgeObservation {
        val count = callCount.get()
        val identity = caller.get()
        return if (count == 0) {
            InternalBridgeObservation(0, 0, 0)
        } else {
            checkNotNull(identity)
            InternalBridgeObservation(identity.pid, identity.uid, count)
        }
    }

    fun close() {
        closed.set(true)
    }

    private fun verifyWorkerCaller(): WorkerBinderObservation? {
        val expected = workerObservation()
        val actualPid = Binder.getCallingPid()
        val actualUid = Binder.getCallingUid()
        if (expected == null || actualPid != expected.pid || actualUid != expected.uid) {
            onViolation()
            return null
        }
        return expected
    }

    private fun pinCallerAndCount(observed: WorkerBinderObservation): Boolean {
        caller.compareAndSet(null, observed)
        if (caller.get() != observed) return false
        callCount.incrementAndGet()
        return true
    }

    private fun respondFailure(
        callback: IJvmHostBridgeCallback,
        call: JvmHostCall,
        code: String,
        message: String,
    ) {
        val response = JvmHostResponse(
            requestId = request.requestId,
            callId = call.callId,
            succeeded = false,
            errorCode = code,
            errorMessage = message,
        )
        runCatching { callback.onResponse(JvmSourceCodec.encodeHostResponse(response)) }
    }

    private fun validPayload(call: JvmHostCall): Boolean = when (call.method) {
        METHOD_APP_LAUNCH -> APP_LAUNCH_PAYLOAD.matches(call.payloadJson)
        METHOD_TOAST_SHOW -> runCatching { JvmToastPayload.decode(call.payloadJson) }.isSuccess
        else -> false
    }

    private companion object {
        const val METHOD_APP_LAUNCH = "app.launch"
        const val METHOD_TOAST_SHOW = "toast.show"
        val SUPPORTED_METHODS = setOf(METHOD_APP_LAUNCH, METHOD_TOAST_SHOW)
        val APP_LAUNCH_PAYLOAD = Regex(
            "\\{\\\"packageName\\\":\\\"[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+\\\"\\}",
        )
    }
}
