package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteJvmScriptContextTest {
    @Test
    fun consoleWritesUtf8LinesToOwnedWorkerStreams() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val context = context(
            capabilities = listOf(JvmScriptCapability.CONSOLE_STREAM),
            stdout = stdout,
            stderr = stderr,
        )

        context.console().log("M5 log 你好")
        context.console().error("M5 error 错误")

        assertEquals("M5 log 你好${System.lineSeparator()}", stdout.toString(Charsets.UTF_8.name()))
        assertEquals("M5 error 错误${System.lineSeparator()}", stderr.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun sleepIsInterruptedByWorkerCancellation() {
        val cancellation = WorkerCancellation().also { it.attach(Thread.currentThread()) }
        val context = context(
            capabilities = listOf(JvmScriptCapability.SLEEP),
            cancellation = cancellation,
        )
        val canceller = Thread {
            Thread.sleep(30L)
            cancellation.cancel(JvmCancellationReason.REQUESTED)
        }.also(Thread::start)

        assertThrows(JvmCancellationException::class.java) { context.sleep(5_000L) }
        canceller.join()
    }

    @Test
    fun ungrantedCapabilityFailsBeforeAnyHostDispatch() {
        val context = context(capabilities = listOf(JvmScriptCapability.SLEEP))

        assertThrows(IllegalArgumentException::class.java) { context.toast("not granted") }
    }

    private fun context(
        capabilities: List<JvmScriptCapability>,
        cancellation: WorkerCancellation = WorkerCancellation(),
        stdout: ByteArrayOutputStream = ByteArrayOutputStream(),
        stderr: ByteArrayOutputStream = ByteArrayOutputStream(),
    ): RemoteJvmScriptContext = RemoteJvmScriptContext(
        request = request(capabilities),
        bridge = object : IJvmHostBridge.Stub() {
            override fun dispatch(request: ByteArray?, callback: IJvmHostBridgeCallback?) {
                error("Host dispatch was not expected")
            }

            override fun destroy(reason: ByteArray?) = Unit
        },
        workerCancellation = cancellation,
        expectedCompilerPid = 1,
        expectedCompilerUid = 1,
        stdout = PrintStream(stdout, true, Charsets.UTF_8.name()),
        stderr = PrintStream(stderr, true, Charsets.UTF_8.name()),
    )

    private fun request(capabilities: List<JvmScriptCapability>): JvmSourceRequest {
        val source = "class Main".toByteArray()
        return JvmSourceRequest(
            requestId = JvmRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = JvmProtocolVersion(
                JvmSourceContract.PROTOCOL_MAJOR,
                JvmSourceContract.PROTOCOL_MINOR,
            ),
            language = JvmSourceLanguage.KOTLIN,
            sourceFileName = "Main.kt",
            sourceSizeBytes = source.size.toLong(),
            sourceSha256 = JvmSha256.digest(source),
            expectedToolchainFingerprint = JvmSha256.digest("toolchain".toByteArray()),
            entryClassName = "Main",
            minApi = JvmSourceContract.MIN_ANDROID_API,
            timeoutMillis = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            allowedHostCalls = capabilities.mapNotNull(JvmScriptCapability::hostMethod),
            grantedCapabilities = capabilities,
        )
    }
}
