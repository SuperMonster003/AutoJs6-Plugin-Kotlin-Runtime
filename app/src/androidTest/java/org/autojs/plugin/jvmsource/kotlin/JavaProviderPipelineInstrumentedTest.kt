package org.autojs.plugin.jvmsource.kotlin

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmAppApi
import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.kotlin.worker.WorkerDexLoader
import org.autojs.plugin.jvmsource.kotlin.worker.WorkerDexLoadStage
import org.autojs.plugin.jvmsource.kotlin.worker.WorkerEntryFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import java.util.UUID

/**
 * Provider-internal Android runtime smoke only. Canonical host-to-provider Binder evidence lives in
 * the host application's instrumentation suite so this test cannot weaken production caller checks.
 */
@RunWith(AndroidJUnit4::class)
class KotlinProviderPipelineInstrumentedTest {
    @Test
    fun kotlinD8DexValidationAndEntryInvocationRunOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val environment = JavaProviderEnvironment.get(context)
        PrivateSessionWorkspace.create(context).use { workspace ->
            FileOutputStream(workspace.sourceFile).use { output ->
                output.write(SOURCE.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }

            val compilation = KotlinJvmCompiler(environment.compilerClasspath).compile(
                sourceFile = workspace.sourceFile,
                outputDirectory = workspace.classesDirectory,
                ensureActive = {},
            )
            assertTrue(
                "Kotlin/JVM must compile the fixed entry source: ${compilation.diagnostics}",
                compilation.succeeded,
            )

            val classes = UserClassJarWriter.write(workspace.classesDirectory, workspace.programJar)
            assertEquals(setOf("LMain;"), classes.dexDescriptors)
            val dexFile = D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                programJar = workspace.programJar,
                outputDirectory = workspace.d8OutputDirectory,
                minApi = JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            val dexIdentity = ProviderDigests.file(
                dexFile,
                JvmSourceContract.MAX_DEX_ARTIFACT_BYTES,
            )
            assertTrue("Generated classes.dex must be frozen before dynamic loading", dexFile.setReadOnly())

            val descriptor = ParcelFileDescriptor.open(dexFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val loader = WorkerDexLoader(context)
            val validatedDex = loader.validateStructure(
                descriptor = descriptor,
                expectedSizeBytes = dexIdentity.sizeBytes,
                expectedSha256 = dexIdentity.sha256,
                expectedClassDescriptors = classes.dexDescriptors,
                requestMinApi = JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            assertEquals(WorkerDexLoadStage.STRUCTURE_VALIDATED, validatedDex.stage)
            loader.createClassLoader(
                validated = validatedDex,
                generation = 1L,
                requestId = UUID.randomUUID().toString(),
                parent = AutoJsJvmEntry::class.java.classLoader!!,
            ).use { loaded ->
                assertEquals(WorkerDexLoadStage.ART_CLASS_LOADER_CREATED, loaded.stage)
                assertEquals(loaded.validatedArtifact.loaderKind, loaded.actualLoaderKind)
                assertEquals(
                    DexRuntimePolicy.loaderKind(android.os.Build.VERSION.SDK_INT),
                    loaded.validatedArtifact.loaderKind,
                )
                assertEquals(
                    JvmDexRuntimeProfile.loaderKindForApi(android.os.Build.VERSION.SDK_INT),
                    loaded.actualLoaderKind.apiKind,
                )
                assertTrue(
                    loaded.validatedArtifact.version in
                        JvmDexRuntimeProfile.admittedVersions(android.os.Build.VERSION.SDK_INT),
                )
                val loadedEntry = WorkerEntryFactory.loadFromArt(loaded.classLoader)
                assertEquals(WorkerDexLoadStage.ART_ENTRY_CLASS_LOADED, loadedEntry.stage)
                val main = WorkerEntryFactory.instantiate(loadedEntry)
                assertEquals(true, main.run(NoHostCallsContext))
            }
        }
    }

    private object NoHostCallsContext : JvmScriptContext {
        private val app = object : JvmAppApi {
            override fun launch(packageName: String): Boolean =
                error("The internal smoke source must not issue host calls")
        }
        private val cancellation = object : JvmCancellation {
            override fun isCancellationRequested(): Boolean = false

            override fun throwIfCancellationRequested() = Unit
        }

        override fun app(): JvmAppApi = app

        override fun cancellation(): JvmCancellation = cancellation
    }

    private companion object {
        val SOURCE = """
            class Main : org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                override fun run(context: org.autojs.plugin.jvmsource.api.JvmScriptContext): Any = true
            }
        """.trimIndent()
    }
}
