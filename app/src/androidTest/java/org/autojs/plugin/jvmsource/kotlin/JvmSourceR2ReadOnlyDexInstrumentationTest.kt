package org.autojs.plugin.jvmsource.kotlin

import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.kotlin.worker.FileReadOnlyDexWriteTarget
import org.autojs.plugin.jvmsource.kotlin.worker.PrivateDexArtifactPublisher
import org.autojs.plugin.jvmsource.kotlin.worker.ReadOnlyDexWritePolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Exact API 34/36 real-filesystem producer for the Android 14 read-only-before-write rule. */
@RunWith(AndroidJUnit4::class)
class JvmSourceR2ReadOnlyDexInstrumentationTest {
    @Test
    fun diskDexIsReadOnlyBeforeFirstContentByte() {
        assertTrue("Readonly evidence is exact API 34/36 only", Build.VERSION.SDK_INT == 34 || Build.VERSION.SDK_INT == 36)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext.applicationContext
        assertEquals(PROVIDER_PACKAGE, context.packageName)
        assertTrue("Readonly evidence requires targetSdk 34+", context.applicationInfo.targetSdkVersion >= 34)
        assertEquals("2.3.21", BuildConfig.KOTLIN_COMPILER_VERSION)
        assertEquals("8.13.17", BuildConfig.D8_VERSION)

        val canonicalRunId = requireBoundedArgument(arguments.getString(ARG_RUN_ID), ARG_RUN_ID, 64)
        UUID.fromString(canonicalRunId)
        val sourceTreeSha256 = requireSha256(arguments.getString(ARG_SOURCE_TREE), ARG_SOURCE_TREE)
        val hostApkSha256 = installedApkSha256(HOST_PACKAGE)
        val providerApkSha256 = installedApkSha256(PROVIDER_PACKAGE)
        assertEquals(hostApkSha256, requireSha256(arguments.getString(ARG_HOST_APK), ARG_HOST_APK))
        assertEquals(providerApkSha256, requireSha256(arguments.getString(ARG_PROVIDER_APK), ARG_PROVIDER_APK))
        assertEquals(currentSignerDigests(HOST_PACKAGE), currentSignerDigests(PROVIDER_PACKAGE))

        val dexBytes = buildRealDex(context)
        assertTrue(dexBytes.size >= 0x70)
        assertEquals("dex\n", dexBytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))

        val base = File(
            AndroidPrivateDirectoryAnchor.codeCache(context),
            "jvm-source-r2-readonly",
        ).absoluteFile
        val basePreexisting = base.exists()
        assertTrue(base.mkdirs() || base.isDirectory)
        assertEquals(base.path, base.canonicalPath)
        val root = File(base, "request-${UUID.randomUUID()}").absoluteFile
        assertTrue(root.mkdir())
        assertTrue(root.canonicalPath.startsWith(base.canonicalPath + File.separator))
        val dexFile = File(root, "classes.dex")
        val stages = mutableListOf<String>()
        var readOnlyBeforeFirstContentByte = false
        var firstContentByteObserved = false
        val productionTarget = FileReadOnlyDexWriteTarget(dexFile)
        val auditedTarget = object : ReadOnlyDexWritePolicy.Target {
            override fun createEmpty() {
                productionTarget.createEmpty()
                stages += "CREATE_EMPTY"
                assertEquals(0L, dexFile.length())
            }

            override fun openForWrite(): ReadOnlyDexWritePolicy.OpenOutput {
                val output = productionTarget.openForWrite()
                stages += "OPEN_STREAM"
                return object : ReadOnlyDexWritePolicy.OpenOutput {
                    override fun writeContent(bytes: ByteArray) {
                        stages += "WRITE_CONTENT"
                        assertTrue("The production target wrote before the audited write callback", dexFile.length() == 0L)
                        val mode = Os.stat(dexFile.path).st_mode
                        val writableBits = OsConstants.S_IWUSR or OsConstants.S_IWGRP or OsConstants.S_IWOTH
                        readOnlyBeforeFirstContentByte = mode and writableBits == 0
                        assertTrue("classes.dex retained a write bit before its first content byte", readOnlyBeforeFirstContentByte)
                        firstContentByteObserved = true
                        output.writeContent(bytes)
                    }

                    override fun sync() {
                        stages += "SYNC"
                        output.sync()
                    }

                    override fun close() = output.close()
                }
            }

            override fun markReadOnly() {
                productionTarget.markReadOnly()
                stages += "MARK_READ_ONLY"
                val mode = Os.stat(dexFile.path).st_mode
                val writableBits = OsConstants.S_IWUSR or OsConstants.S_IWGRP or OsConstants.S_IWOTH
                assertTrue("Production target did not remove every write bit", mode and writableBits == 0)
            }
        }

        var failureCleanupVerified = false
        var partialArtifactPublished = false
        var partialBytesWrittenBeforeFailure = 0L
        var failureReadOnlyBeforeWrite = false
        var failureRoot: File? = null
        var failureDex: File? = null
        try {
            ReadOnlyDexWritePolicy.write(dexBytes, auditedTarget)
            assertEquals(WRITE_SEQUENCE, stages)
            assertTrue(firstContentByteObserved)
            assertTrue(readOnlyBeforeFirstContentByte)
            assertEquals(dexBytes.size.toLong(), dexFile.length())
            assertEquals(sha256(dexBytes), ProviderDigests.file(dexFile).sha256.toHexString())

            assertTrue("Unable to remove successful readonly DEX fixture", dexFile.delete())
            assertTrue("Unable to remove successful readonly fixture directory", root.delete())

            val controlledFailureRoot = File(base, "request-${UUID.randomUUID()}").absoluteFile
            failureRoot = controlledFailureRoot
            assertTrue(controlledFailureRoot.mkdir())
            assertTrue(controlledFailureRoot.canonicalPath.startsWith(base.canonicalPath + File.separator))
            val controlledFailureDex = File(controlledFailureRoot, "classes.dex")
            failureDex = controlledFailureDex
            val injectedFailure = runCatching {
                PrivateDexArtifactPublisher.publish(
                    root = controlledFailureRoot,
                    bytes = dexBytes,
                    targetFactory = { productionDex ->
                        assertEquals(controlledFailureDex.path, productionDex.path)
                        val failureTarget = FileReadOnlyDexWriteTarget(productionDex)
                        object : ReadOnlyDexWritePolicy.Target {
                            override fun createEmpty() = failureTarget.createEmpty()

                            override fun openForWrite(): ReadOnlyDexWritePolicy.OpenOutput {
                                val output = failureTarget.openForWrite()
                                return object : ReadOnlyDexWritePolicy.OpenOutput {
                                    override fun writeContent(bytes: ByteArray) {
                                        val mode = Os.stat(productionDex.path).st_mode
                                        val writeBits =
                                            OsConstants.S_IWUSR or OsConstants.S_IWGRP or OsConstants.S_IWOTH
                                        failureReadOnlyBeforeWrite = mode and writeBits == 0
                                        assertTrue(failureReadOnlyBeforeWrite)
                                        output.writeContent(bytes.copyOfRange(0, 16.coerceAtMost(bytes.size)))
                                        partialBytesWrittenBeforeFailure = productionDex.length()
                                        assertTrue(partialBytesWrittenBeforeFailure > 0L)
                                        throw IOException("controlled failure after a non-zero partial write")
                                    }

                                    override fun sync() = output.sync()

                                    override fun close() = output.close()
                                }
                            }

                            override fun markReadOnly() = failureTarget.markReadOnly()
                        }
                    },
                ) {
                    partialArtifactPublished = true
                }
            }.exceptionOrNull()
            assertNotNull("Controlled partial-write failure did not escape production publisher", injectedFailure)
            assertFalse("A failed write crossed the publication boundary", partialArtifactPublished)
            assertTrue("Failure fixture was not readonly before its partial write", failureReadOnlyBeforeWrite)
            assertTrue("Failure did not occur after a non-zero partial write", partialBytesWrittenBeforeFailure > 0L)
            assertFalse("Production publisher retained the failed DEX", controlledFailureDex.exists())
            assertFalse("Production publisher retained the failed request root", controlledFailureRoot.exists())
            failureCleanupVerified = !controlledFailureDex.exists() && !controlledFailureRoot.exists()
            assertTrue("Failure fixture cleanup was not verified", failureCleanupVerified)
            val ownedBaseCleanupVerified = if (basePreexisting) {
                true
            } else {
                assertTrue("Test-owned readonly base was not empty", base.list()?.isEmpty() == true)
                assertTrue("Unable to remove test-owned readonly base", base.delete())
                !base.exists()
            }

            val observation = JSONObject()
                .put("schemaVersion", 1)
                .put("evidenceType", OUTPUT_TYPE)
                .put("canonicalRunId", canonicalRunId)
                .put("collectedAtUtc", Instant.now().toString())
                .put("sourceTreeSha256", sourceTreeSha256)
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                .put("buildFingerprint", Build.FINGERPRINT)
                .put("targetSdkAtLeast34", true)
                .put("writeSequence", JSONArray(stages))
                .put("readOnlyBeforeFirstContentByte", true)
                .put("firstContentByteObserved", true)
                .put("failureInjected", true)
                .put("failurePoint", "AFTER_NONZERO_PARTIAL_WRITE_BEFORE_SYNC")
                .put("failureReadOnlyBeforeWrite", failureReadOnlyBeforeWrite)
                .put("partialBytesWrittenBeforeFailure", partialBytesWrittenBeforeFailure)
                .put("partialArtifactPublished", partialArtifactPublished)
                .put("productionCleanupVerified", failureCleanupVerified)
                .put("failureCleanupVerified", failureCleanupVerified)
                .put("baseDirectoryOwnedByTest", !basePreexisting)
                .put("ownedBaseCleanupVerified", ownedBaseCleanupVerified)
                .put("dexSizeBytes", dexBytes.size)
                .put("dexSha256", sha256(dexBytes))
                .put("providerProcess", currentProcessName())
                .put("providerPid", Process.myPid())
                .put("providerUid", Process.myUid())
                .put("hostApkSha256", hostApkSha256)
                .put("providerApkSha256", providerApkSha256)
                .put("result", "observed")
                .put("selectorCompletionMustComeFromRunner", true)
            val line = "$OUTPUT_PREFIX$observation"
            Log.i(TAG, line)
            println(line)
        } finally {
            if (dexFile.exists()) assertTrue("Unable to remove readonly DEX evidence fixture", dexFile.delete())
            if (root.exists()) assertTrue("Unable to remove readonly evidence fixture directory", root.delete())
            failureDex?.takeIf(File::exists)?.let { assertTrue("Unable to remove failed DEX fixture", it.delete()) }
            failureRoot?.takeIf(File::exists)?.let { assertTrue("Unable to remove failed fixture directory", it.delete()) }
            if (!basePreexisting && base.exists() && base.list()?.isEmpty() == true) {
                assertTrue("Unable to remove empty test-owned readonly base", base.delete())
            }
        }
    }

    private fun buildRealDex(context: android.content.Context): ByteArray {
        val environment = JavaProviderEnvironment.get(context)
        return PrivateSessionWorkspace.create(context, sourceFileName = "Main.kt").use { workspace ->
            workspace.sourceFile.writeText(SOURCE, Charsets.UTF_8)
            val compilation = KotlinJvmCompiler(environment.compilerClasspath).compile(
                sourceFile = workspace.sourceFile,
                outputDirectory = workspace.classesDirectory,
                ensureActive = {},
            )
            assertTrue("Kotlin/JVM failed before readonly DEX production", compilation.succeeded)
            UserClassJarWriter.write(workspace.classesDirectory, workspace.programJar)
            val dex = D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                workspace.programJar,
                workspace.d8OutputDirectory,
                JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            val identity = ProviderDigests.file(dex, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
            require(identity.sizeBytes <= Int.MAX_VALUE.toLong())
            dex.readBytes().also { bytes -> require(bytes.size.toLong() == identity.sizeBytes) }
        }
    }

    private fun requireBoundedArgument(value: String?, label: String, maximumChars: Int): String {
        require(!value.isNullOrBlank() && value.length <= maximumChars) { "$label is missing or exceeds its bound" }
        return value
    }

    private fun requireSha256(value: String?, label: String): String {
        val bounded = requireBoundedArgument(value, label, 64)
        require(SHA256.matches(bounded)) { "$label is not a lowercase SHA-256" }
        return bounded
    }

    private fun installedApkSha256(packageName: String): String {
        val applicationInfo = checkNotNull(packageInfo(packageName).applicationInfo) {
            "Installed package applicationInfo is unavailable: $packageName"
        }
        val source = File(checkNotNull(applicationInfo.sourceDir) { "Installed APK path is unavailable: $packageName" })
        require(source.isFile && source.length() in 1L..MAX_APK_BYTES) { "Installed APK is absent or exceeds its bound" }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileInputStream(source).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total = Math.addExact(total, read.toLong())
                require(total <= MAX_APK_BYTES)
                digest.update(buffer, 0, read)
            }
        }
        require(total == source.length()) { "Installed APK changed while hashing" }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String) = InstrumentationRegistry.getInstrumentation()
        .targetContext.packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
        )

    @Suppress("DEPRECATION")
    private fun currentSignerDigests(packageName: String): Set<String> {
        val info = packageInfo(packageName)
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            checkNotNull(info.signingInfo).apkContentsSigners
        } else {
            checkNotNull(info.signatures)
        }
        return signatures.map { sha256(it.toByteArray()) }.toSortedSet()
    }

    private fun currentProcessName(): String {
        val bytes = ByteArray(MAX_PROCESS_NAME_BYTES)
        var count = 0
        FileInputStream("/proc/self/cmdline").use { input ->
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                if (read == 0) break
                if (read > 0) count += read
            }
            require(count < bytes.size || input.read() < 0) { "Current process name exceeds its bound" }
        }
        require(count > 0) { "Unable to read the current process name" }
        val end = (0 until count).firstOrNull { index -> bytes[index] == 0.toByte() } ?: count
        return bytes.copyOfRange(0, end).toString(Charsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val TAG = "JvmSourceR2ReadOnly"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.kotlin.runtime"
        const val ARG_RUN_ID = "r2.readOnly.canonicalRunId"
        const val ARG_SOURCE_TREE = "r2.readOnly.sourceTreeSha256"
        const val ARG_HOST_APK = "r2.readOnly.hostApkSha256"
        const val ARG_PROVIDER_APK = "r2.readOnly.providerApkSha256"
        const val OUTPUT_TYPE = "r2-readonly-dex-runtime-observation"
        const val OUTPUT_PREFIX = "JVM_SOURCE_R2_READONLY_DEX_OBSERVATION="
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
        const val MAX_PROCESS_NAME_BYTES = 512
        val SHA256 = Regex("[0-9a-f]{64}")
        val WRITE_SEQUENCE = listOf("CREATE_EMPTY", "OPEN_STREAM", "MARK_READ_ONLY", "WRITE_CONTENT", "SYNC")
        val SOURCE = """
            class Main : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any = true
            }
        """.trimIndent()
    }
}
