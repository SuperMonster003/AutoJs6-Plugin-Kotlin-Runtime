package org.autojs.plugin.jvmsource.kotlin.m7harness

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class M7ProviderHarnessInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val runId: String
        get() = InstrumentationRegistry.getArguments().getString(ARG_RUN_ID)
            ?.takeIf { it.length <= 64 }
            ?: UUID.randomUUID().toString()

    @Test
    fun benchmarkFiveCacheColdAndWarmPairs() {
        M7ProviderClient(context).use { client ->
            val cacheCold = ArrayList<Long>()
            val cacheWarm = ArrayList<Long>()
            val cacheColdExecution = ArrayList<Long>()
            val cacheWarmExecution = ArrayList<Long>()
            val coldWorkers = ArrayList<Int>()
            val warmWorkers = ArrayList<Int>()
            repeat(BENCHMARK_PAIR_COUNT) { index ->
                val source = benchmarkSource(runId, index)
                val first = completed(client.run(source))
                cacheCold += first.compilationElapsedMillis
                cacheColdExecution += first.executionElapsedMillis
                coldWorkers += first.workerPid
                val second = completed(client.run(source))
                cacheWarm += second.compilationElapsedMillis
                cacheWarmExecution += second.executionElapsedMillis
                warmWorkers += second.workerPid
                assertEquals(first.dexArtifactSha256, second.dexArtifactSha256)
                assertTrue(second.workerGeneration > first.workerGeneration)
            }
            val coldMedian = median(cacheCold)
            val warmMedian = median(cacheWarm)
            assertTrue(
                "Cache-warm median must be below cache-cold median: cold=$cacheCold warm=$cacheWarm",
                warmMedian < coldMedian,
            )
            assertEquals(BENCHMARK_PAIR_COUNT * 2, (coldWorkers + warmWorkers).distinct().size)
            emit(
                BENCHMARK_PREFIX,
                baseEvidence(client)
                    .put("cacheColdCompilationMillis", JSONArray(cacheCold))
                    .put("cacheWarmCompilationMillis", JSONArray(cacheWarm))
                    .put("cacheColdExecutionMillis", JSONArray(cacheColdExecution))
                    .put("cacheWarmExecutionMillis", JSONArray(cacheWarmExecution))
                    .put("cacheColdMedianMillis", coldMedian)
                    .put("cacheWarmMedianMillis", warmMedian)
                    .put("cacheColdExecutionMedianMillis", median(cacheColdExecution))
                    .put("cacheWarmExecutionMedianMillis", median(cacheWarmExecution))
                    .put("expectedTelemetryDelta", JSONObject()
                        .put("hits", BENCHMARK_PAIR_COUNT)
                        .put("misses", BENCHMARK_PAIR_COUNT)
                        .put("publications", BENCHMARK_PAIR_COUNT)
                        .put("publicationFailures", 0))
                    .put("workerPidCount", (coldWorkers + warmWorkers).distinct().size)
                    .put("result", "observed"),
            )
        }
    }

    @Test
    fun fiftySequentialSessionsRetireEveryWorker() {
        M7ProviderClient(context).use { client ->
            val source = controlledSource(runId, infiniteWhenLaunched = false)
            completed(client.run(benchmarkSource("$runId-resource-warmup", 999)))
            completed(
                client.run(
                    controlledSource("$runId-controlled-warmup", infiniteWhenLaunched = false),
                    controlledProfile(launchResult = false),
                ),
            )
            failed(client.run(invalidSource("$runId-invalid-warmup")))
            val resourceBefore = if (client.providerDebuggable) captureCompilerSnapshot() else null
            val compilerPids = ArrayList<Int>()
            val workerPids = ArrayList<Int>()
            val workerGenerations = ArrayList<Long>()
            var resourceMidpoint: CompilerResourceSnapshot? = null
            repeat(STRESS_MIX_CYCLE_COUNT) { cycle ->
                repeat(STRESS_SUCCESS_PER_CYCLE) {
                    val outcome = client.run(source, controlledProfile(launchResult = false))
                    val result = completed(outcome)
                    compilerPids += outcome.started.compilerPid
                    workerPids += result.workerPid
                    workerGenerations += result.workerGeneration
                    assertEquals("true", result.resultJson)
                    assertEquals(1, outcome.hostCallCount)
                }
                if (cycle % 2 == 0) {
                    val outcome = client.run(invalidSource(runId))
                    val error = failed(outcome)
                    compilerPids += outcome.started.compilerPid
                    assertEquals(JvmSourceErrorCode.COMPILATION_FAILED, error.code)
                    assertEquals(JvmSourceFailurePhase.COMPILATION, error.phase)
                    assertTrue(outcome.diagnostics.isNotEmpty())
                } else {
                    client.start(
                        source.toByteArray(Charsets.UTF_8),
                        controlledProfile(launchResult = true, timeoutMillis = 30_000L),
                    ).use { active ->
                        assertTrue("Worker did not issue its control host call", active.awaitHostCall())
                        active.cancel()
                        val outcome = active.awaitTerminal()
                        compilerPids += outcome.started.compilerPid
                        val cancellation = cancelled(outcome)
                        assertEquals(JvmCancellationReason.REQUESTED, cancellation.reason)
                        assertEquals(JvmSourceFailurePhase.EXECUTION, cancellation.phase)
                    }
                }
                if (cycle == STRESS_MIX_CYCLE_COUNT / 2 - 1 && client.providerDebuggable) {
                    resourceMidpoint = captureCompilerSnapshot()
                }
            }
            assertEquals(STRESS_TOTAL_COUNT, compilerPids.size)
            assertEquals(1, compilerPids.distinct().size)
            assertEquals(STRESS_SUCCESS_COUNT, workerGenerations.distinct().size)
            assertTrue(workerGenerations.zipWithNext().all { (first, second) -> second > first })
            assertTrue(workerPids.zipWithNext().all { (first, second) -> first != second })
            val resourceAfter = if (client.providerDebuggable) captureCompilerSnapshot() else null
            if (resourceBefore != null && resourceMidpoint != null && resourceAfter != null) {
                assertEquals(resourceBefore.pid, resourceAfter.pid)
                assertEquals(resourceBefore.pid, resourceMidpoint.pid)
                assertEquals(0, resourceBefore.workspaceDirectoryCount)
                assertEquals(0, resourceMidpoint.workspaceDirectoryCount)
                assertEquals(0, resourceAfter.workspaceDirectoryCount)
                assertTrue(
                    "Open descriptors grew beyond tolerance: before=$resourceBefore after=$resourceAfter",
                    resourceAfter.openFileDescriptorCount - resourceBefore.openFileDescriptorCount <=
                        MAX_OPEN_FD_GROWTH,
                )
                assertTrue(
                    "Compiler RSS grew beyond the per-epoch budget: before=$resourceBefore " +
                        "after=$resourceAfter",
                    resourceAfter.rssBytes - resourceBefore.rssBytes <= MAX_COMPILER_RSS_GROWTH_BYTES,
                )
                assertTrue(
                    "Compiler RSS exceeded the per-epoch absolute budget: after=$resourceAfter",
                    resourceAfter.rssBytes <= MAX_COMPILER_RSS_BYTES,
                )
            }
            emit(
                STRESS_PREFIX,
                baseEvidence(client)
                    .put("sessionCount", compilerPids.size)
                    .put("successfulSessions", STRESS_SUCCESS_COUNT)
                    .put("compilationFailures", STRESS_COMPILE_FAILURE_COUNT)
                    .put("executionCancellations", STRESS_CANCEL_COUNT)
                    .put("compilerPid", compilerPids.distinct().single())
                    .put("workerPids", JSONArray(workerPids))
                    .put("workerGenerations", JSONArray(workerGenerations))
                    .put("strictlyIncreasingWorkerGenerations", true)
                    .put("consecutiveWorkerPidsDiffer", true)
                    .put("expectedTelemetryDelta", JSONObject()
                        .put("hits", if (client.providerDebuggable) 0 else 44)
                        .put("misses", if (client.providerDebuggable) STRESS_TOTAL_COUNT else 6)
                        .put("publications", if (client.providerDebuggable) 0 else 1)
                        .put("publicationFailures", 0))
                    .put("compilerResourceBefore", resourceBefore?.toJson() ?: JSONObject.NULL)
                    .put("compilerResourceMidpoint", resourceMidpoint?.toJson() ?: JSONObject.NULL)
                    .put("compilerResourceAfter", resourceAfter?.toJson() ?: JSONObject.NULL)
                    .put(
                        "compilerResourceDelta",
                        if (resourceBefore != null && resourceAfter != null) {
                            JSONObject()
                                .put("rssBytes", resourceAfter.rssBytes - resourceBefore.rssBytes)
                                .put(
                                    "steadyStateRssBytes",
                                    resourceMidpoint?.let { resourceAfter.rssBytes - it.rssBytes }
                                        ?: JSONObject.NULL,
                                )
                                .put(
                                    "openFileDescriptorCount",
                                    resourceAfter.openFileDescriptorCount - resourceBefore.openFileDescriptorCount,
                                )
                        } else {
                            JSONObject.NULL
                        },
                    )
                    .put("result", "observed"),
            )
        }
    }

    @Test
    fun oversizedSourceIsRejectedAtInput() {
        M7ProviderClient(context).use { client ->
            val bytes = ByteArray((JvmSourceContract.MAX_SOURCE_BYTES + 1L).toInt()) { ' '.code.toByte() }
            val outcome = client.start(
                bytes,
                M7RequestProfile(declaredSourceSizeBytes = JvmSourceContract.MAX_SOURCE_BYTES),
            ).use(M7ActiveSession::awaitTerminal)
            val error = failed(outcome)
            assertEquals(JvmSourceErrorCode.SOURCE_TOO_LARGE, error.code)
            assertEquals(JvmSourceFailurePhase.INPUT, error.phase)
            emitFault(client, "oversized-source", error.code.name, error.phase.name)
        }
    }

    @Test
    fun m8SourceShapeAndDiagnosticContract() {
        M7ProviderClient(context).use { client ->
            val flexible = completed(
                client.run(
                    """
                        class ScriptEntry : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                            override fun run(
                                context: org.autojs.plugin.jvmsource.api.JvmScriptContext,
                            ): Any = "flexible-entry"
                        }
                    """.trimIndent(),
                    M7RequestProfile(
                        sourceFileName = "ScriptEntry.kt",
                        entryClassName = "ScriptEntry",
                    ),
                ),
            )
            assertEquals("\"flexible-entry\"", flexible.resultJson)

            val bomOutcome = client.run("\uFEFFval value: String = 1")
            val bomError = failed(bomOutcome)
            assertEquals(JvmSourceErrorCode.COMPILATION_FAILED, bomError.code)
            assertEquals(JvmSourceFailurePhase.COMPILATION, bomError.phase)
            val located = bomOutcome.diagnostics.first { diagnostic ->
                diagnostic.code == "KOTLIN_ERROR" && diagnostic.line != null
            }
            assertEquals("Main.kt", located.sourceFileName)
            assertEquals(1, located.line)
            assertEquals(19, located.column)
            assertTrue(located.message.contains("mismatch", ignoreCase = true))

            val asciiOnly = failed(client.run("package `escaped-name`\nclass Main"))
            assertEquals(JvmSourceErrorCode.INVALID_REQUEST, asciiOnly.code)
            assertEquals(JvmSourceFailurePhase.INPUT, asciiOnly.phase)
            assertEquals(ASCII_PACKAGE_MESSAGE, asciiOnly.message)

            val mismatch = failed(client.run("package sample.actual\nclass Main"))
            assertEquals(JvmSourceErrorCode.INVALID_REQUEST, mismatch.code)
            assertEquals(JvmSourceFailurePhase.INPUT, mismatch.phase)
            assertEquals(PACKAGE_ENTRY_MISMATCH_MESSAGE, mismatch.message)

            val missingEntry = failed(client.run("class Main"))
            assertEquals(JvmSourceErrorCode.ENTRY_POINT_MISSING, missingEntry.code)
            assertEquals(JvmSourceFailurePhase.COMPILATION, missingEntry.phase)
            assertEquals(ENTRY_INTERFACE_MESSAGE, missingEntry.message)

            emit(
                M8_SOURCE_DIAGNOSTIC_PREFIX,
                baseEvidence(client)
                    .put("flexibleEntryResult", flexible.resultJson)
                    .put("bomDiagnostic", JSONObject()
                        .put("code", located.code)
                        .put("sourceFileName", located.sourceFileName)
                        .put("line", located.line)
                        .put("column", located.column))
                    .put("asciiPackageError", asciiOnly.message)
                    .put("packageMismatchError", mismatch.message)
                    .put("entryInterfaceError", missingEntry.message)
                    .put("result", "observed"),
            )
        }
    }

    @Test
    fun compileStarvationTimesOutAtCompilation() {
        M7ProviderClient(context).use { client ->
            val outcome = client.run(
                compileStarvationSource(runId),
                M7RequestProfile(timeoutMillis = COMPILE_TIMEOUT_MILLIS),
            )
            val cancellation = cancelled(outcome)
            assertEquals(JvmCancellationReason.TIMEOUT, cancellation.reason)
            assertEquals(JvmSourceFailurePhase.COMPILATION, cancellation.phase)
            emitFault(client, "compile-timeout", cancellation.reason.name, cancellation.phase.name)
        }
    }

    @Test
    fun executionInfiniteLoopTimesOutAndIsKilled() {
        M7ProviderClient(context).use { client ->
            val source = controlledSource(runId, infiniteWhenLaunched = true)
            completed(client.run(source, controlledProfile(launchResult = false)))
            val outcome = client.run(
                source,
                controlledProfile(launchResult = true, timeoutMillis = EXECUTION_TIMEOUT_MILLIS),
            )
            val cancellation = cancelled(outcome)
            assertEquals(JvmCancellationReason.TIMEOUT, cancellation.reason)
            assertEquals(JvmSourceFailurePhase.EXECUTION, cancellation.phase)
            assertTrue(cancellation.elapsedMillis >= EXECUTION_TIMEOUT_MILLIS)
            emitFault(client, "execution-infinite-loop", cancellation.reason.name, cancellation.phase.name)
        }
    }

    @Test
    fun externalWorkerKillIsReported() {
        M7ProviderClient(context).use { client ->
            val source = controlledSource(runId, infiniteWhenLaunched = true)
            completed(client.run(source, controlledProfile(launchResult = false)))
            client.start(
                source.toByteArray(Charsets.UTF_8),
                controlledProfile(launchResult = true, timeoutMillis = EXTERNAL_KILL_TIMEOUT_MILLIS),
            ).use { active ->
                assertTrue("Worker did not reach the externally-killable loop", active.awaitHostCall())
                val marker = "$EXTERNAL_KILL_READY_PREFIX$runId"
                Log.i(TAG, marker)
                println(marker)
                context.sendBroadcast(
                    Intent(DEBUG_WORKER_KILL_ACTION).setComponent(
                        ComponentName(M7ProviderClient.PROVIDER_PACKAGE, DEBUG_WORKER_KILL_RECEIVER),
                    ),
                )
                val outcome = active.awaitTerminal()
                val error = failed(outcome)
                assertEquals(JvmSourceErrorCode.WORKER_DIED, error.code)
                assertEquals(JvmSourceFailurePhase.EXECUTION, error.phase)
                emitFault(client, "external-worker-kill", error.code.name, error.phase.name)
            }
        }
    }

    private fun controlledProfile(
        launchResult: Boolean,
        timeoutMillis: Long = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
    ) = M7RequestProfile(
        timeoutMillis = timeoutMillis,
        allowedHostCalls = listOf(M7ProviderClient.METHOD_APP_LAUNCH),
        grantedCapabilities = listOf(JvmScriptCapability.APP_LAUNCH),
        launchResult = launchResult,
    )

    private fun completed(outcome: M7SessionOutcome) =
        (outcome.terminal as? M7Terminal.Completed)?.result
            ?: throw AssertionError("Expected success, observed ${outcome.terminal}")

    private fun failed(outcome: M7SessionOutcome) =
        (outcome.terminal as? M7Terminal.Failed)?.error
            ?: throw AssertionError("Expected failure, observed ${outcome.terminal}")

    private fun cancelled(outcome: M7SessionOutcome) =
        (outcome.terminal as? M7Terminal.Cancelled)?.cancellation
            ?: throw AssertionError("Expected cancellation, observed ${outcome.terminal}")

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    private fun captureCompilerSnapshot(): CompilerResourceSnapshot {
        val latch = CountDownLatch(1)
        val result = AtomicReference<CompilerResourceSnapshot?>()
        val failure = AtomicReference<Throwable?>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    check(resultCode == Activity.RESULT_OK) {
                        "Debug compiler snapshot receiver returned $resultCode"
                    }
                    val extras = checkNotNull(getResultExtras(false))
                    result.set(
                        CompilerResourceSnapshot(
                            pid = extras.requiredInt(DEBUG_EXTRA_PID),
                            rssBytes = extras.requiredLong(DEBUG_EXTRA_RSS_BYTES),
                            openFileDescriptorCount = extras.requiredInt(DEBUG_EXTRA_OPEN_FD_COUNT),
                            workspaceDirectoryCount = extras.requiredInt(DEBUG_EXTRA_WORKSPACE_COUNT),
                        ),
                    )
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    latch.countDown()
                }
            }
        }
        context.sendOrderedBroadcast(
            Intent(DEBUG_COMPILER_SNAPSHOT_ACTION).setComponent(
                ComponentName(M7ProviderClient.PROVIDER_PACKAGE, DEBUG_COMPILER_SNAPSHOT_RECEIVER),
            ),
            null,
            receiver,
            Handler(Looper.getMainLooper()),
            Activity.RESULT_CANCELED,
            null,
            null,
        )
        check(latch.await(DEBUG_CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Debug compiler snapshot timed out"
        }
        failure.get()?.let { throw it }
        return checkNotNull(result.get())
    }

    private fun Bundle.requiredInt(name: String): Int {
        check(containsKey(name)) { "Debug snapshot omitted '$name'" }
        return getInt(name)
    }

    private fun Bundle.requiredLong(name: String): Long {
        check(containsKey(name)) { "Debug snapshot omitted '$name'" }
        return getLong(name)
    }

    private fun emitFault(client: M7ProviderClient, scenario: String, code: String, phase: String) {
        emit(
            FAULT_PREFIX,
            baseEvidence(client)
                .put("scenario", scenario)
                .put("code", code)
                .put("phase", phase)
                .put("result", "observed"),
        )
    }

    private fun baseEvidence(client: M7ProviderClient): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("runId", runId)
        .put("providerPackage", M7ProviderClient.PROVIDER_PACKAGE)
        .put("providerVersionName", client.providerVersionName)
        .put("providerVersionCode", client.providerVersionCode)
        .put("providerDebuggable", client.providerDebuggable)
        .put("hostPackage", context.packageName)
        .put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("fingerprint", Build.FINGERPRINT))

    private fun emit(prefix: String, evidence: JSONObject) {
        val line = "$prefix$evidence"
        Log.i(TAG, line)
        println(line)
    }

    private fun benchmarkSource(token: String, index: Int): String = """
        class Main : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
            override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any =
                "${token.take(32)}-$index"
        }
    """.trimIndent()

    private fun controlledSource(token: String, infiniteWhenLaunched: Boolean): String {
        val controlledBranch = if (infiniteWhenLaunched) {
            "while (true) { /* cancellation hard-kill fixture */ }"
        } else {
            "Thread.sleep(60_000L)"
        }
        return """
            class Main : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any {
                    val launch = context.app().launch("org.autojs.m7.control")
                    if (launch) $controlledBranch
                    return "${token.take(32)}".isNotEmpty()
                }
            }
        """.trimIndent()
    }

    private fun invalidSource(token: String): String = """
        class Main : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
            override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any =
                missingM7Symbol${token.filter(Char::isLetterOrDigit).take(16)}
        }
    """.trimIndent()

    private fun compileStarvationSource(token: String): String = buildString {
        appendLine("class Main : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {")
        appendLine("override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any = f0(1)")
        repeat(COMPILE_STARVATION_FUNCTIONS) { index ->
            val next = (index + 1) % COMPILE_STARVATION_FUNCTIONS
            appendLine("private fun f$index(value: Int): Int = if (value < 0) f$next(value + 1) else value + $index")
        }
        appendLine("private val token = \"${token.take(32)}\"")
        appendLine("}")
    }

    private companion object {
        const val TAG = "M7Harness"
        const val ARG_RUN_ID = "m7RunId"
        const val BENCHMARK_PREFIX = "M7_BENCHMARK_EVIDENCE="
        const val STRESS_PREFIX = "M7_STRESS_EVIDENCE="
        const val FAULT_PREFIX = "M7_FAULT_EVIDENCE="
        const val M8_SOURCE_DIAGNOSTIC_PREFIX = "M8_SOURCE_DIAGNOSTIC_EVIDENCE="
        const val EXTERNAL_KILL_READY_PREFIX = "M7_EXTERNAL_WORKER_KILL_READY="
        const val DEBUG_WORKER_KILL_ACTION =
            "io.github.supermonster003.autojs6.plugin.kotlin.runtime.debug.KILL_WORKER"
        const val DEBUG_WORKER_KILL_RECEIVER =
            "org.autojs.plugin.jvmsource.kotlin.worker.DebugWorkerKillReceiver"
        const val DEBUG_COMPILER_SNAPSHOT_ACTION =
            "io.github.supermonster003.autojs6.plugin.kotlin.runtime.debug.CAPTURE_COMPILER_SNAPSHOT"
        const val DEBUG_COMPILER_SNAPSHOT_RECEIVER =
            "org.autojs.plugin.jvmsource.kotlin.DebugCompilerSnapshotReceiver"
        const val DEBUG_EXTRA_PID = "pid"
        const val DEBUG_EXTRA_RSS_BYTES = "rssBytes"
        const val DEBUG_EXTRA_OPEN_FD_COUNT = "openFileDescriptorCount"
        const val DEBUG_EXTRA_WORKSPACE_COUNT = "workspaceDirectoryCount"
        const val DEBUG_CONTROL_TIMEOUT_SECONDS = 10L
        const val MAX_OPEN_FD_GROWTH = 4
        const val MAX_COMPILER_RSS_GROWTH_BYTES = 64L * 1024L * 1024L
        const val MAX_COMPILER_RSS_BYTES = 256L * 1024L * 1024L
        const val BENCHMARK_PAIR_COUNT = 5
        const val STRESS_SUCCESS_COUNT = 40
        const val STRESS_COMPILE_FAILURE_COUNT = 5
        const val STRESS_CANCEL_COUNT = 5
        const val STRESS_TOTAL_COUNT = 50
        const val STRESS_MIX_CYCLE_COUNT = 10
        const val STRESS_SUCCESS_PER_CYCLE = 4
        const val COMPILE_STARVATION_FUNCTIONS = 20
        const val COMPILE_TIMEOUT_MILLIS = 200L
        const val EXECUTION_TIMEOUT_MILLIS = 2_000L
        const val EXTERNAL_KILL_TIMEOUT_MILLIS = 60_000L
        const val ASCII_PACKAGE_MESSAGE =
            "Kotlin package declarations support only ordinary ASCII identifiers; " +
                "escaped or non-ASCII identifiers are not supported"
        const val PACKAGE_ENTRY_MISMATCH_MESSAGE =
            "Kotlin package does not match the requested entry class"
        const val ENTRY_INTERFACE_MESSAGE =
            "Requested Kotlin entry class must implement AutoJsJvmEntry"
    }

    private data class CompilerResourceSnapshot(
        val pid: Int,
        val rssBytes: Long,
        val openFileDescriptorCount: Int,
        val workspaceDirectoryCount: Int,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("pid", pid)
            .put("rssBytes", rssBytes)
            .put("openFileDescriptorCount", openFileDescriptorCount)
            .put("workspaceDirectoryCount", workspaceDirectoryCount)
    }
}
