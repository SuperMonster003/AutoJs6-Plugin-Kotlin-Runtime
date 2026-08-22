package org.autojs.plugin.jvmsource.kotlin.worker

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.kotlin.AndroidPrivateDirectoryAnchor
import org.autojs.plugin.jvmsource.kotlin.DexArtifactValidator
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import org.autojs.plugin.jvmsource.kotlin.ValidatedDexArtifact
import org.autojs.plugin.jvmsource.kotlin.WorkerDexLoaderKind
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

internal class WorkerDexLoader(private val context: Context) {
    /** Gate 1: consume, bind, and structurally validate the DEX without constructing a ClassLoader. */
    fun validateStructure(
        descriptor: ParcelFileDescriptor,
        expectedSizeBytes: Long,
        expectedSha256: JvmSha256,
        expectedClassDescriptors: Set<String>,
        requestMinApi: Int,
        ensureActive: () -> Unit,
    ): StructurallyValidatedWorkerDex {
        val bytes = readDex(descriptor, expectedSizeBytes, ensureActive)
        val artifact = DexArtifactValidator.validate(
            bytes = bytes,
            expectedSizeBytes = expectedSizeBytes,
            expectedSha256 = expectedSha256,
            requestMinApi = requestMinApi,
            deviceApi = Build.VERSION.SDK_INT,
            expectedClassDescriptors = expectedClassDescriptors,
        )
        ensureActive()
        return StructurallyValidatedWorkerDex(bytes, artifact)
    }

    /** Gate 2a: construct the exact ClassLoader selected by the validated runtime policy. */
    fun createClassLoader(
        validated: StructurallyValidatedWorkerDex,
        generation: Long,
        requestId: String,
        parent: ClassLoader,
    ): LoadedDex {
        return try {
            when (validated.artifact.loaderKind) {
                WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER -> loadInMemory(validated, parent)
                WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER ->
                    loadFromPrivateFile(validated, generation, requestId, parent)
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: JvmCancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.CLASS_LOADING_FAILED,
                JvmSourceFailurePhase.WORKER_START,
                "ART ClassLoader creation failed after DEX structure validation",
                error,
            )
        }
    }

    private fun readDex(
        descriptor: ParcelFileDescriptor,
        expectedSizeBytes: Long,
        ensureActive: () -> Unit,
    ): ByteArray {
        if (expectedSizeBytes !in 1L..JvmSourceContract.MAX_DEX_ARTIFACT_BYTES ||
            expectedSizeBytes > Int.MAX_VALUE
        ) {
            throw invalidArtifact("DEX size metadata is outside the worker limit")
        }
        val expected = expectedSizeBytes.toInt()
        val bytes = ByteArray(expected)
        var offset = 0
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                while (offset < expected) {
                    ensureActive()
                    val read = input.read(bytes, offset, expected - offset)
                    if (read < 0) break
                    if (read > 0) offset += read
                }
                require(offset == expected) { "DEX stream ended before its declared size" }
                require(input.read() < 0) { "DEX stream exceeds its declared size" }
                descriptor.checkError()
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: JvmCancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            throw invalidArtifact("Unable to read the compiler DEX stream", error)
        }
        return bytes
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun loadInMemory(validated: StructurallyValidatedWorkerDex, parent: ClassLoader): LoadedDex {
        // ART requires a non-direct buffer to expose its backing array while constructing the
        // in-memory DEX. The validated bytes remain private to this isolated worker.
        val buffer = ByteBuffer.wrap(validated.bytes)
        return LoadedDex(
            classLoader = InMemoryDexClassLoader(buffer, parent),
            validatedArtifact = validated.artifact,
            actualLoaderKind = WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            retainedBytes = validated.bytes,
        )
    }

    private fun loadFromPrivateFile(
        validated: StructurallyValidatedWorkerDex,
        generation: Long,
        requestId: String,
        parent: ClassLoader,
    ): LoadedDex {
        require(REQUEST_ID.matches(requestId)) { "Worker request ID is invalid" }
        val privateCodeCache = AndroidPrivateDirectoryAnchor.codeCache(context)
        val lexicalBase = File(privateCodeCache, "jvm-source-worker").absoluteFile
        if (!lexicalBase.exists() && !lexicalBase.mkdirs()) throw IOException("Unable to create worker code cache")
        val base = lexicalBase.canonicalFile
        require(base.path == lexicalBase.path && base.isDirectory) {
            "Worker code cache must be an ordinary private directory"
        }
        val root = File(base, "request-$requestId-generation-$generation").canonicalFile
        val expectedPrefix = base.path + File.separator
        require(root.path.startsWith(expectedPrefix) && root.mkdir()) { "Unable to allocate worker code cache" }
        return PrivateDexArtifactPublisher.publish(root, validated.bytes) { dex ->
            val optimized = File(root, "optimized")
            if (!optimized.mkdir()) throw IOException("Unable to create worker optimized cache")
            LoadedDex(
                DexClassLoader(dex.absolutePath, optimized.absolutePath, null, parent),
                validatedArtifact = validated.artifact,
                actualLoaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
                cleanupRoot = root,
            )
        }
    }

    private fun invalidArtifact(message: String, cause: Throwable? = null) = JavaProviderFailure(
        JvmSourceErrorCode.ARTIFACT_INVALID,
        JvmSourceFailurePhase.WORKER_START,
        message,
        cause,
    )

    companion object {
        private val REQUEST_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val STALE_DIRECTORY = Regex("request-${REQUEST_ID.pattern}-generation-[1-9][0-9]*")

        fun clearStalePrivateDex(context: Context) {
            val privateCodeCache = AndroidPrivateDirectoryAnchor.codeCache(context)
            val lexicalBase = File(privateCodeCache, "jvm-source-worker").absoluteFile
            if (!lexicalBase.exists()) return
            val base = lexicalBase.canonicalFile
            if (base.path != lexicalBase.path || !base.isDirectory) {
                throw IOException("Worker code cache must be an ordinary private directory")
            }
            val candidates = base.listFiles()
                ?: throw IOException("Unable to enumerate worker code cache")
            candidates.forEach { candidate ->
                if (!STALE_DIRECTORY.matches(candidate.name)) return@forEach
                val lexicalCandidate = File(base, candidate.name).absoluteFile
                val canonical = candidate.canonicalFile
                if (candidate.absoluteFile.path != lexicalCandidate.path ||
                    canonical.path != lexicalCandidate.path || !candidate.isDirectory
                ) return@forEach
                deletePrivateTreeWithoutFollowingLinks(candidate)
            }
        }
    }
}

/**
 * The single production publication boundary for private disk DEX. A file is publishable only
 * after the read-only write returns; every failure removes the entire request root without
 * following links before the exception escapes.
 */
internal object PrivateDexArtifactPublisher {
    fun <T> publish(
        root: File,
        bytes: ByteArray,
        targetFactory: (File) -> ReadOnlyDexWritePolicy.Target = ::FileReadOnlyDexWriteTarget,
        publish: (File) -> T,
    ): T {
        val lexicalRoot = root.absoluteFile
        require(root.canonicalFile.path == lexicalRoot.path && root.isDirectory) {
            "Private DEX publication root must be an ordinary directory"
        }
        val dex = File(lexicalRoot, "classes.dex")
        return try {
            ReadOnlyDexWritePolicy.write(bytes, targetFactory(dex))
            publish(dex)
        } catch (error: Throwable) {
            runCatching { deletePrivateTreeWithoutFollowingLinks(lexicalRoot) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }
}

internal enum class WorkerDexLoadStage {
    STRUCTURE_VALIDATED,
    ART_CLASS_LOADER_CREATED,
    ART_ENTRY_CLASS_LOADED,
}

internal class StructurallyValidatedWorkerDex internal constructor(
    internal val bytes: ByteArray,
    val artifact: ValidatedDexArtifact,
) {
    val stage: WorkerDexLoadStage = WorkerDexLoadStage.STRUCTURE_VALIDATED
}

internal class LoadedDex(
    val classLoader: ClassLoader,
    val validatedArtifact: ValidatedDexArtifact,
    val actualLoaderKind: WorkerDexLoaderKind,
    @Suppress("unused") private val retainedBytes: ByteArray? = null,
    private val cleanupRoot: File? = null,
) : Closeable {
    val stage: WorkerDexLoadStage = WorkerDexLoadStage.ART_CLASS_LOADER_CREATED

    init {
        require(actualLoaderKind == validatedArtifact.loaderKind) {
            "Actual ART ClassLoader branch differs from the validated DEX runtime policy"
        }
    }

    override fun close() {
        if (!closeAndVerifyTemporaryStorageReleased()) {
            throw IOException("Worker code-cache cleanup was not verified")
        }
    }

    internal fun closeAndVerifyTemporaryStorageReleased(): Boolean {
        val root = cleanupRoot ?: return true
        val lexicalParent = root.absoluteFile.parentFile
            ?: throw IOException("Worker code-cache request has no private parent")
        val canonicalParent = lexicalParent.canonicalFile
        if (canonicalParent.path != lexicalParent.path || !canonicalParent.isDirectory) {
            throw IOException("Worker code-cache parent is no longer an ordinary directory")
        }
        deletePrivateTreeWithoutFollowingLinks(root)
        val remaining = canonicalParent.list()
            ?: throw IOException("Unable to verify worker code-cache cleanup")
        return remaining.none { it == root.name }
    }
}

private fun deletePrivateTreeWithoutFollowingLinks(root: File) {
    fun delete(node: File, expectedLexicalPath: File) {
        val lexical = node.absoluteFile
        require(lexical.path == expectedLexicalPath.absoluteFile.path) {
            "Worker code-cache cleanup escaped its lexical root"
        }
        val canonical = runCatching { node.canonicalFile }.getOrNull()
        if (canonical == null || canonical.path != lexical.path) {
            deleteListedWorkerEntry(node, "Unable to remove a worker code-cache link")
            return
        }
        if (node.isDirectory) {
            val children = node.listFiles()
                ?: throw IOException("Unable to enumerate worker code cache")
            children.forEach { child -> delete(child, File(lexical, child.name)) }
        }
        deleteListedWorkerEntry(node, "Unable to remove a worker code-cache entry")
    }

    delete(root, root.absoluteFile)
}

private fun deleteListedWorkerEntry(node: File, failureMessage: String) {
    val listed = node.parentFile?.list()?.any { it == node.name } == true
    if (listed && !node.delete()) throw IOException(failureMessage)
}
