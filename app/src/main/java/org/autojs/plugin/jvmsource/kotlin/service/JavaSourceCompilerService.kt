package org.autojs.plugin.jvmsource.kotlin.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmSourceCallback
import org.autojs.plugin.jvmsource.api.IJvmSourceProvider
import org.autojs.plugin.jvmsource.api.IJvmSourceSession
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.kotlin.JavaProviderEnvironment
import org.autojs.plugin.jvmsource.kotlin.JavaProviderRuntime
import org.autojs.plugin.jvmsource.kotlin.PrivateSessionWorkspace
import org.autojs.plugin.jvmsource.kotlin.ProviderProcessIdentity
import org.autojs.plugin.jvmsource.kotlin.SingleActiveSessionGate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val processSessionGate = SingleActiveSessionGate<RemoteJavaSourceSession>()

class JavaSourceCompilerService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var compilerIdentity: ProviderProcessIdentity
    private val compilerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jvm-source-java-compiler").apply { isDaemon = true }
    }
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "jvm-source-java-timeouts").apply { isDaemon = true }
    }
    private val callbackLane = SerialCallbackLane()
    private val sessions = ConcurrentHashMap.newKeySet<RemoteJavaSourceSession>()
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        JavaProviderEnvironment.get(applicationContext)
    }
    private val capabilities by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        JavaProviderRuntime.capabilities(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        CompilerProcessMemoryIsolation.enforceNonDumpable()
        callerVerifier = HostCallerVerifier(this)
        compilerIdentity = ProviderProcessIdentity.current(this, ":compiler")
        PrivateSessionWorkspace.clearStale(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        val retiringSessions = sessions.toList()
        retiringSessions.forEach(RemoteJavaSourceSession::serviceDestroyed)
        armIndependentCompilerProcessKill(retiringSessions)
        sessions.clear()
        compilerExecutor.shutdownNow()
        scheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private fun armIndependentCompilerProcessKill(retiringSessions: List<RemoteJavaSourceSession>) {
        val policy = CompilerServiceShutdownPolicy()
        val plan = policy.begin(retiringSessions.count(RemoteJavaSourceSession::hasCriticalWorkForServiceShutdown))
        if (!plan.armIndependentProcessKill) return
        Thread(
            {
                try {
                    Thread.sleep(plan.graceMillis)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                val live = retiringSessions.count(RemoteJavaSourceSession::hasCriticalWorkForServiceShutdown)
                if (policy.shouldKillAfterGrace(live)) Process.killProcess(Process.myPid())
            },
            "jvm-source-compiler-shutdown-watchdog",
        ).apply {
            isDaemon = false
            start()
        }
    }

    private val binder = object : IJvmSourceProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return JvmSourceCodec.encodeProviderInfo(JavaProviderRuntime.providerInfo())
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return JvmSourceCodec.encodeCapabilities(this@JavaSourceCompilerService.capabilities)
        }

        override fun openSession(
            request: ByteArray?,
            sourceFd: ParcelFileDescriptor?,
            stdoutFd: ParcelFileDescriptor?,
            stderrFd: ParcelFileDescriptor?,
            hostBridge: IJvmHostBridge?,
            callback: IJvmSourceCallback?,
        ): IJvmSourceSession {
            val ownerUid = try {
                callerVerifier.enforceAllowedCaller()
            } catch (error: Throwable) {
                OwnedSessionDescriptors.closeIncoming(sourceFd, stdoutFd, stderrFd)
                throw error
            }
            if (request == null || sourceFd == null || stdoutFd == null || stderrFd == null ||
                hostBridge == null || callback == null
            ) {
                OwnedSessionDescriptors.closeIncoming(sourceFd, stdoutFd, stderrFd)
                throw IllegalArgumentException("Kotlin source session arguments must not be null")
            }
            val decodedRequest = try {
                JvmSourceCodec.decodeRequest(request.copyOf()).also { value ->
                    JvmSourceValidation.validateRequestAgainst(
                        value,
                        this@JavaSourceCompilerService.capabilities,
                        JavaProviderRuntime.protocolVersion,
                    )
                }
            } catch (error: Throwable) {
                OwnedSessionDescriptors.closeIncoming(sourceFd, stdoutFd, stderrFd)
                throw IllegalArgumentException("Kotlin source request metadata is invalid", error)
            }
            val descriptors = OwnedSessionDescriptors.duplicateBeforeAsync(
                sourceFd,
                stdoutFd,
                stderrFd,
            )
            val session = try {
                RemoteJavaSourceSession(
                    context = applicationContext,
                    ownerUid = ownerUid,
                    request = decodedRequest,
                    descriptors = descriptors,
                    hostBridge = hostBridge,
                    callback = callback,
                    capabilities = this@JavaSourceCompilerService.capabilities,
                    environment = environment,
                    compilerIdentity = compilerIdentity,
                    callerVerifier = callerVerifier,
                    compilerExecutor = compilerExecutor,
                    scheduler = scheduler,
                    callbackLane = callbackLane,
                    onFinished = { finished ->
                        processSessionGate.release(finished)
                        sessions.remove(finished)
                    },
                )
            } catch (error: Throwable) {
                descriptors.close()
                throw error
            }
            sessions += session
            if (processSessionGate.tryAcquire(session)) session.start() else session.rejectBusy()
            return session
        }
    }
}
