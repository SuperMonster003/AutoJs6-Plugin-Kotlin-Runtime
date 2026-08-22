package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class CachedCompilationArtifacts(
    val cacheKey: CompilationArtifactCacheKey,
    val programJar: File,
    val dexFile: File,
    val classSummary: UserClassJarSummary,
    val classIdentity: ProviderFileIdentity,
    val dexIdentity: ProviderFileIdentity,
    val dexVersion: String,
    val loaderKind: WorkerDexLoaderKind,
)

internal enum class CompilationCacheMissReason {
    NOT_FOUND,
    INVALID_OR_EXPIRED,
    CACHE_UNAVAILABLE,
    MATERIALIZATION_FAILED,
    CACHE_DISABLED,
    PROVIDER_IDENTITY_UNAVAILABLE,
    PROVIDER_IDENTITY_DRIFTED,
}

internal enum class CompilationCacheLookupOutcome {
    HIT,
    MISS,
}

/** Deliberately omits cache paths, keys, source identity, and artifact identity. */
internal data class CompilationCacheRequestObservation(
    val outcome: CompilationCacheLookupOutcome,
    val missReason: CompilationCacheMissReason?,
) {
    init {
        require((outcome == CompilationCacheLookupOutcome.HIT) == (missReason == null))
    }
}

internal data class CompilationCacheLookup(
    val artifacts: CachedCompilationArtifacts?,
    val missReason: CompilationCacheMissReason?,
)

/**
 * Provider-private, bounded compilation cache. Published entry directories are immutable and
 * become visible with one same-filesystem directory rename only after every artifact validates.
 */
internal class CompilationArtifactCache internal constructor(
    private val rootDirectory: File,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maximumBytes: Long = DEFAULT_MAXIMUM_BYTES,
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
    authenticationKey: ByteArray = newAuthenticationKey(),
    private val fileWriter: CompilationCacheFileWriter = JvmCompilationCacheFileWriter,
    private val treeCleaner: CompilationCacheTreeCleaner = JvmCompilationCacheTreeCleaner,
) {
    private val authenticationKey = authenticationKey.copyOf()

    init {
        require(ttlMillis > 0L && maximumBytes > 0L && maximumEntries > 0)
        require(this.authenticationKey.size >= AUTHENTICATION_KEY_BYTES)
    }

    @Synchronized
    fun lookup(
        cacheKey: CompilationArtifactCacheKey,
        requestMinApi: Int,
        deviceApi: Int,
        entryClassName: String = "Main",
    ): CachedCompilationArtifacts? =
        lookupObserved(cacheKey, requestMinApi, deviceApi, entryClassName).artifacts

    @Synchronized
    fun lookupObserved(
        cacheKey: CompilationArtifactCacheKey,
        requestMinApi: Int,
        deviceApi: Int,
        entryClassName: String = "Main",
    ): CompilationCacheLookup {
        val root = ensureRoot()
        val candidateWasListed = root.list()?.any { it == entryName(cacheKey) } == true
        maintain(root, protectedKey = cacheKey)
        val entry = File(root, entryName(cacheKey)).absoluteFile
        val loaded = loadEntry(entry, cacheKey, requestMinApi, deviceApi, entryClassName).getOrElse {
            deleteRecognizedTree(entry, root)
            return CompilationCacheLookup(
                artifacts = null,
                missReason = if (candidateWasListed) {
                    CompilationCacheMissReason.INVALID_OR_EXPIRED
                } else {
                    CompilationCacheMissReason.NOT_FOUND
                },
            )
        }
        return CompilationCacheLookup(loaded, missReason = null)
    }

    @Synchronized
    fun publish(
        cacheKey: CompilationArtifactCacheKey,
        programJar: File,
        dexFile: File,
        classSummary: UserClassJarSummary,
        classIdentity: ProviderFileIdentity,
        dexIdentity: ProviderFileIdentity,
        requestMinApi: Int,
        deviceApi: Int,
        entryClassName: String = "Main",
        ensureActive: () -> Unit,
    ): CachedCompilationArtifacts {
        ensureActive()
        val root = ensureRoot()
        maintain(root, protectedKey = cacheKey)
        val destination = File(root, entryName(cacheKey)).absoluteFile
        loadEntry(destination, cacheKey, requestMinApi, deviceApi, entryClassName).getOrNull()?.let { return it }
        deleteRecognizedTree(destination, root)

        val temporary = allocateTemporary(root)
        var published = false
        try {
            ensureActive()
            val cachedJar = File(temporary, PROGRAM_JAR)
            val cachedDex = File(temporary, CLASSES_DEX)
            copyFrozen(programJar, cachedJar, JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES, ensureActive)
            copyFrozen(dexFile, cachedDex, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES, ensureActive)

            val rebuiltClass = UserClassJarValidator.validate(cachedJar, entryClassName)
            require(rebuiltClass.first == classIdentity && rebuiltClass.second == classSummary) {
                "Class artifact changed before cache publication"
            }
            val rebuiltDexIdentity = ProviderDigests.file(cachedDex, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
            require(rebuiltDexIdentity == dexIdentity) { "DEX artifact changed before cache publication" }
            val dexBytes = readBounded(cachedDex, dexIdentity.sizeBytes, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
            val validatedDex = DexArtifactValidator.validate(
                bytes = dexBytes,
                expectedSizeBytes = dexIdentity.sizeBytes,
                expectedSha256 = dexIdentity.sha256,
                requestMinApi = requestMinApi,
                deviceApi = deviceApi,
                expectedClassDescriptors = classSummary.dexDescriptors,
            )

            val manifest = CacheManifest(
                keyHex = cacheKey.hex,
                createdAtMillis = clockMillis(),
                classIdentity = classIdentity,
                dexIdentity = dexIdentity,
                classSummary = classSummary,
                requestMinApi = requestMinApi,
                validatedDeviceApi = deviceApi,
                dexVersion = validatedDex.version,
                loaderKind = validatedDex.loaderKind,
            )
            val manifestBytes = encodeManifest(manifest)
            require(manifestBytes.size.toLong() <= MAX_MANIFEST_BYTES)
            // The completion digest is staged first; manifest.bin is the final file write before rename.
            writeFrozen(
                File(temporary, COMPLETE),
                manifestHmac(manifestBytes).toHex().toByteArray(Charsets.US_ASCII),
            )
            writeFrozen(File(temporary, MANIFEST), manifestBytes)
            require(temporary.list().orEmpty().toSet() == EXPECTED_FILES) {
                "Compilation cache staging directory is incomplete"
            }
            require(treeBytes(temporary) <= maximumBytes) {
                "Compilation cache entry exceeds the total cache byte quota"
            }
            ensureActive()
            if (!temporary.renameTo(destination)) throw IOException("Unable to atomically publish cache entry")
            published = true
            ensureActive()
            val result = loadEntry(
                destination,
                cacheKey,
                requestMinApi,
                deviceApi,
                entryClassName,
            ).getOrThrow()
            maintain(root, protectedKey = cacheKey)
            return result
        } catch (error: Throwable) {
            val cleanupTarget = if (published) destination else temporary
            runCatching { deleteRecognizedTree(cleanupTarget, root) }.onFailure(error::addSuppressed)
            throw error
        } finally {
            if (!published) runCatching { deleteRecognizedTree(temporary, root) }
        }
    }

    /** Copies a verified hit into the fresh per-session workspace and verifies the copies again. */
    @Synchronized
    fun materialize(
        cached: CachedCompilationArtifacts,
        destinationProgramJar: File,
        destinationDexFile: File,
        requestMinApi: Int,
        deviceApi: Int,
        entryClassName: String = "Main",
        ensureActive: () -> Unit,
    ): CachedCompilationArtifacts {
        ensureActive()
        copyFrozen(
            cached.programJar,
            destinationProgramJar,
            JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES,
            ensureActive,
        )
        copyFrozen(
            cached.dexFile,
            destinationDexFile,
            JvmSourceContract.MAX_DEX_ARTIFACT_BYTES,
            ensureActive,
        )
        val rebuiltClass = UserClassJarValidator.validate(destinationProgramJar, entryClassName)
        require(rebuiltClass.first == cached.classIdentity && rebuiltClass.second == cached.classSummary) {
            "Materialized class artifact differs from the verified cache hit"
        }
        val rebuiltDex = ProviderDigests.file(destinationDexFile, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
        require(rebuiltDex == cached.dexIdentity) {
            "Materialized DEX differs from the verified cache hit"
        }
        val validatedDex = DexArtifactValidator.validate(
            bytes = readBounded(destinationDexFile, rebuiltDex.sizeBytes, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES),
            expectedSizeBytes = rebuiltDex.sizeBytes,
            expectedSha256 = rebuiltDex.sha256,
            requestMinApi = requestMinApi,
            deviceApi = deviceApi,
            expectedClassDescriptors = rebuiltClass.second.dexDescriptors,
        )
        require(validatedDex.version == cached.dexVersion && validatedDex.loaderKind == cached.loaderKind) {
            "Materialized DEX runtime profile differs from the verified cache hit"
        }
        ensureActive()
        return CachedCompilationArtifacts(
            cacheKey = cached.cacheKey,
            programJar = destinationProgramJar,
            dexFile = destinationDexFile,
            classSummary = rebuiltClass.second,
            classIdentity = rebuiltClass.first,
            dexIdentity = rebuiltDex,
            dexVersion = validatedDex.version,
            loaderKind = validatedDex.loaderKind,
        )
    }

    @Synchronized
    fun invalidate(cacheKey: CompilationArtifactCacheKey) {
        val root = ensureRoot()
        deleteRecognizedTree(File(root, entryName(cacheKey)).absoluteFile, root)
    }

    private fun loadEntry(
        entry: File,
        expectedKey: CompilationArtifactCacheKey,
        requestMinApi: Int,
        deviceApi: Int,
        entryClassName: String,
    ): Result<CachedCompilationArtifacts> = runCatching {
        val root = rootDirectory.absoluteFile.canonicalFile
        requireOrdinaryExactChild(entry, root, directory = true)
        val listed = entry.listFiles() ?: throw IOException("Unable to enumerate compilation cache entry")
        require(listed.map(File::getName).toSet() == EXPECTED_FILES && listed.size == EXPECTED_FILES.size) {
            "Compilation cache entry is incomplete"
        }
        listed.forEach {
            requireOrdinaryExactChild(it, entry, directory = false)
            require(!it.canWrite()) { "Compilation cache file is not immutable" }
        }

        val manifestFile = File(entry, MANIFEST)
        val manifestIdentity = ProviderDigests.file(manifestFile, MAX_MANIFEST_BYTES)
        val manifestBytes = readBounded(manifestFile, manifestIdentity.sizeBytes, MAX_MANIFEST_BYTES)
        verifyManifestAuthentication(entry, manifestBytes)
        val manifest = decodeManifest(manifestBytes)
        require(manifest.keyHex == expectedKey.hex) { "Compilation cache key differs from its directory" }
        require(manifest.publicationState == PUBLICATION_COMPLETE) {
            "Compilation cache entry was not completely published"
        }
        require(manifest.requestMinApi == requestMinApi && manifest.validatedDeviceApi == deviceApi) {
            "Compilation cache runtime provenance changed"
        }
        val age = Math.subtractExact(clockMillis(), manifest.createdAtMillis)
        require(age in 0L..ttlMillis) { "Compilation cache entry is expired or from the future" }

        val programJar = File(entry, PROGRAM_JAR)
        val rebuiltClass = UserClassJarValidator.validate(programJar, entryClassName)
        require(rebuiltClass.first == manifest.classIdentity && rebuiltClass.second == manifest.classSummary) {
            "Compilation cache class summary or digest changed"
        }
        val dexFile = File(entry, CLASSES_DEX)
        val actualDexIdentity = ProviderDigests.file(dexFile, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
        require(actualDexIdentity == manifest.dexIdentity) { "Compilation cache DEX digest changed" }
        val dexBytes = readBounded(
            dexFile,
            manifest.dexIdentity.sizeBytes,
            JvmSourceContract.MAX_DEX_ARTIFACT_BYTES,
        )
        val validatedDex = DexArtifactValidator.validate(
            bytes = dexBytes,
            expectedSizeBytes = manifest.dexIdentity.sizeBytes,
            expectedSha256 = manifest.dexIdentity.sha256,
            requestMinApi = requestMinApi,
            deviceApi = deviceApi,
            expectedClassDescriptors = rebuiltClass.second.dexDescriptors,
        )
        require(validatedDex.version == manifest.dexVersion && validatedDex.loaderKind == manifest.loaderKind) {
            "Compilation cache DEX runtime profile changed"
        }
        CachedCompilationArtifacts(
            cacheKey = expectedKey,
            programJar = programJar,
            dexFile = dexFile,
            classSummary = rebuiltClass.second,
            classIdentity = rebuiltClass.first,
            dexIdentity = actualDexIdentity,
            dexVersion = validatedDex.version,
            loaderKind = validatedDex.loaderKind,
        )
    }

    private fun maintain(root: File, protectedKey: CompilationArtifactCacheKey?) {
        val now = clockMillis()
        val entries = root.listFiles() ?: throw IOException("Unable to enumerate compilation cache root")
        val retained = mutableListOf<RetainedEntry>()
        entries.forEach { candidate ->
            when {
                TEMPORARY_DIRECTORY.matches(candidate.name) -> deleteRecognizedTree(candidate, root)
                ENTRY_DIRECTORY.matches(candidate.name) -> {
                    val key = candidate.name.removePrefix(ENTRY_PREFIX)
                    val metadata = runCatching {
                        requireOrdinaryExactChild(candidate, root, directory = true)
                        val children = candidate.listFiles() ?: throw IOException("Unable to enumerate cache entry")
                        require(children.map(File::getName).toSet() == EXPECTED_FILES &&
                            children.size == EXPECTED_FILES.size)
                        children.forEach {
                            requireOrdinaryExactChild(it, candidate, directory = false)
                            require(!it.canWrite())
                        }
                        val manifest = File(candidate, MANIFEST)
                        val identity = ProviderDigests.file(manifest, MAX_MANIFEST_BYTES)
                        val manifestBytes = readBounded(manifest, identity.sizeBytes, MAX_MANIFEST_BYTES)
                        verifyManifestAuthentication(candidate, manifestBytes)
                        val value = decodeManifest(manifestBytes)
                        require(value.keyHex == key)
                        val age = Math.subtractExact(now, value.createdAtMillis)
                        require(age in 0L..ttlMillis)
                        val bytes = treeBytes(candidate)
                        RetainedEntry(candidate, value.createdAtMillis, bytes, key)
                    }.getOrNull()
                    if (metadata == null) deleteRecognizedTree(candidate, root) else retained += metadata
                }
            }
        }
        var bytes = retained.fold(0L) { total, item -> Math.addExact(total, item.bytes) }
        var count = retained.size
        retained.sortedWith(compareBy<RetainedEntry> { it.createdAtMillis }.thenBy { it.keyHex })
            .forEach { item ->
                if ((count > maximumEntries || bytes > maximumBytes) && item.keyHex != protectedKey?.hex) {
                    deleteRecognizedTree(item.directory, root)
                    count--
                    bytes = Math.subtractExact(bytes, item.bytes)
                }
            }
    }

    private fun ensureRoot(): File {
        val lexical = rootDirectory.absoluteFile
        if (!lexical.exists() && !lexical.mkdirs()) throw IOException("Unable to create compilation cache root")
        val canonical = lexical.canonicalFile
        require(canonical.path == lexical.path && canonical.isDirectory) {
            "Compilation cache root must be an ordinary private directory"
        }
        return canonical
    }

    private fun allocateTemporary(root: File): File {
        repeat(8) {
            val candidate = File(root, ".publish-${UUID.randomUUID()}").absoluteFile
            if (candidate.mkdir()) {
                requireOrdinaryExactChild(candidate, root, directory = true)
                return candidate
            }
        }
        throw IOException("Unable to allocate compilation cache staging directory")
    }

    private fun copyFrozen(source: File, destination: File, maximumBytes: Long, ensureActive: () -> Unit) {
        requireOrdinaryFile(source)
        requireFreshOrdinaryDestination(destination)
        FileInputStream(source).buffered().use { input ->
            fileWriter.openFreshReadOnly(destination).use { output ->
                requireOrdinaryFile(destination)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var written = 0L
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    written = Math.addExact(written, read.toLong())
                    require(written <= maximumBytes) { "Compilation artifact exceeds cache limit" }
                    output.write(buffer, 0, read)
                }
                output.sync()
            }
        }
    }

    private fun writeFrozen(destination: File, bytes: ByteArray) {
        requireFreshOrdinaryDestination(destination)
        fileWriter.openFreshReadOnly(destination).use { output ->
            requireOrdinaryFile(destination)
            output.write(bytes, 0, bytes.size)
            output.sync()
        }
    }

    private fun readBounded(file: File, expectedBytes: Long, maximumBytes: Long): ByteArray {
        require(expectedBytes in 0L..maximumBytes && expectedBytes <= Int.MAX_VALUE)
        require(file.length() == expectedBytes)
        val result = ByteArray(expectedBytes.toInt())
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < result.size) {
                val read = input.read(result, offset, result.size - offset)
                if (read < 0) throw EOFException("Compilation cache file is truncated")
                if (read > 0) offset += read
            }
            require(input.read() < 0) { "Compilation cache file exceeds its declared size" }
        }
        return result
    }

    private fun encodeManifest(value: CacheManifest): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MANIFEST_MAGIC)
            output.writeInt(MANIFEST_SCHEMA)
            output.writeUTF(value.keyHex)
            output.writeUTF(value.publicationState)
            output.writeLong(value.createdAtMillis)
            output.writeInt(value.requestMinApi)
            output.writeInt(value.validatedDeviceApi)
            output.writeUTF(value.dexVersion)
            output.writeUTF(value.loaderKind.name)
            writeIdentity(output, value.classIdentity)
            writeIdentity(output, value.dexIdentity)
            output.writeInt(value.classSummary.classFileCount)
            output.writeInt(value.classSummary.dexDescriptors.size)
            value.classSummary.dexDescriptors.sorted().forEach(output::writeUTF)
        }
        bytes.toByteArray()
    }

    private fun verifyManifestAuthentication(entry: File, manifestBytes: ByteArray) {
        val encoded = readBounded(File(entry, COMPLETE), 64L, 64L).toString(Charsets.US_ASCII)
        require(SHA256_HEX.matches(encoded)) { "Compilation cache completion authenticator is invalid" }
        val expected = encoded.hexToBytes()
        require(MessageDigest.isEqual(expected, manifestHmac(manifestBytes))) {
            "Compilation cache manifest authentication failed for this compiler-process epoch"
        }
    }

    private fun manifestHmac(bytes: ByteArray): ByteArray = Mac.getInstance(HMAC_ALGORITHM).run {
        init(SecretKeySpec(authenticationKey, HMAC_ALGORITHM))
        doFinal(bytes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private fun decodeManifest(bytes: ByteArray): CacheManifest = DataInputStream(bytes.inputStream()).use { input ->
        require(input.readInt() == MANIFEST_MAGIC && input.readInt() == MANIFEST_SCHEMA)
        val key = input.readUTF()
        require(SHA256_HEX.matches(key))
        val publicationState = input.readUTF()
        require(publicationState == PUBLICATION_COMPLETE)
        val createdAtMillis = input.readLong()
        require(createdAtMillis > 0L)
        val requestMinApi = input.readInt()
        val validatedDeviceApi = input.readInt()
        val dexVersion = input.readUTF()
        val loaderKind = WorkerDexLoaderKind.valueOf(input.readUTF())
        require(requestMinApi >= JvmSourceContract.MIN_ANDROID_API && validatedDeviceApi >= requestMinApi)
        require(DexRuntimePolicy.evaluateVersion(validatedDeviceApi, requestMinApi, dexVersion).run {
            accepted && this.loaderKind == loaderKind
        })
        val classIdentity = readIdentity(input, PROGRAM_JAR, JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES)
        val dexIdentity = readIdentity(input, CLASSES_DEX, JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
        val classCount = input.readInt()
        val descriptorCount = input.readInt()
        require(classCount in 1..UserClassJarWriter.MAX_CLASS_FILES && descriptorCount == classCount)
        val descriptors = buildSet {
            repeat(descriptorCount) {
                val descriptor = input.readUTF()
                require(CACHE_DESCRIPTOR.matches(descriptor) && add(descriptor))
            }
        }
        require(input.read() < 0) { "Compilation cache manifest has trailing bytes" }
        CacheManifest(
            key,
            publicationState,
            createdAtMillis,
            requestMinApi,
            validatedDeviceApi,
            dexVersion,
            loaderKind,
            classIdentity,
            dexIdentity,
            UserClassJarSummary(classCount, descriptors),
        )
    }

    private fun writeIdentity(output: DataOutputStream, identity: ProviderFileIdentity) {
        output.writeUTF(identity.name)
        output.writeLong(identity.sizeBytes)
        output.write(identity.sha256.toByteArray())
    }

    private fun readIdentity(input: DataInputStream, expectedName: String, maximumBytes: Long): ProviderFileIdentity {
        val name = input.readUTF()
        val size = input.readLong()
        require(name == expectedName && size in 1L..maximumBytes)
        val digest = ByteArray(JvmSha256.BYTE_COUNT)
        input.readFully(digest)
        return ProviderFileIdentity(name, size, JvmSha256.fromBytes(digest))
    }

    private fun treeBytes(directory: File): Long {
        val children = directory.listFiles() ?: throw IOException("Unable to enumerate compilation cache entry")
        return children.fold(0L) { total, child ->
            requireOrdinaryExactChild(child, directory, directory = false)
            Math.addExact(total, child.length())
        }
    }

    private fun requireOrdinaryFile(file: File) {
        val lexical = file.absoluteFile
        require(lexical.canonicalFile.path == lexical.path && lexical.isFile) {
            "Compilation artifact must be an ordinary file"
        }
    }

    private fun requireFreshOrdinaryDestination(file: File) {
        val parent = file.absoluteFile.parentFile ?: throw IOException("Compilation cache destination has no parent")
        val canonicalParent = parent.canonicalFile
        require(canonicalParent.path == parent.absolutePath && canonicalParent.isDirectory) {
            "Compilation cache destination parent must be an ordinary directory"
        }
        val expected = File(canonicalParent, file.name).absoluteFile
        require(file.absoluteFile.path == expected.path && canonicalParent.list()?.none { it == file.name } == true) {
            "Compilation cache destination must be a fresh exact child"
        }
    }

    private fun requireOrdinaryExactChild(child: File, parent: File, directory: Boolean) {
        val lexicalParent = parent.absoluteFile
        val lexical = File(lexicalParent, child.name).absoluteFile
        require(child.absoluteFile.path == lexical.path && child.canonicalFile.path == lexical.path) {
            "Compilation cache entry escaped its lexical parent"
        }
        require(if (directory) child.isDirectory else child.isFile) {
            "Compilation cache entry has the wrong file type"
        }
    }

    private fun deleteRecognizedTree(candidate: File, expectedParent: File) {
        val nameRecognized = ENTRY_DIRECTORY.matches(candidate.name) || TEMPORARY_DIRECTORY.matches(candidate.name)
        val listed = expectedParent.list()?.any { it == candidate.name } == true
        if (!nameRecognized || !listed) return
        val lexicalParent = expectedParent.absoluteFile
        val lexical = File(lexicalParent, candidate.name).absoluteFile
        require(candidate.absoluteFile.path == lexical.path) { "Cache cleanup escaped its lexical root" }
        treeCleaner.deleteFlatTree(lexical, lexicalParent, EXPECTED_FILES)
    }

    private fun entryName(key: CompilationArtifactCacheKey): String = ENTRY_PREFIX + key.hex

    private data class CacheManifest(
        val keyHex: String,
        val publicationState: String = PUBLICATION_COMPLETE,
        val createdAtMillis: Long,
        val requestMinApi: Int,
        val validatedDeviceApi: Int,
        val dexVersion: String,
        val loaderKind: WorkerDexLoaderKind,
        val classIdentity: ProviderFileIdentity,
        val dexIdentity: ProviderFileIdentity,
        val classSummary: UserClassJarSummary,
    )

    private data class RetainedEntry(
        val directory: File,
        val createdAtMillis: Long,
        val bytes: Long,
        val keyHex: String,
    )

    companion object {
        private const val ENTRY_PREFIX = "entry-"
        private const val PROGRAM_JAR = "program.jar"
        private const val CLASSES_DEX = "classes.dex"
        private const val MANIFEST = "manifest.bin"
        private const val COMPLETE = "complete.hmac"
        private const val MANIFEST_MAGIC = 0x414a5343
        private const val MANIFEST_SCHEMA = 1
        private const val PUBLICATION_COMPLETE = "complete"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val AUTHENTICATION_KEY_BYTES = 32
        private const val MAX_MANIFEST_BYTES = 64L * 1024L
        internal const val DEFAULT_TTL_MILLIS = 24L * 60L * 60L * 1_000L
        internal const val DEFAULT_MAXIMUM_BYTES = 128L * 1024L * 1024L
        internal const val DEFAULT_MAXIMUM_ENTRIES = 8
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
        private val ENTRY_DIRECTORY = Regex("entry-[0-9a-f]{64}")
        private val TEMPORARY_DIRECTORY = Regex(
            "\\.publish-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )
        private val CACHE_DESCRIPTOR = Regex(
            "L[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)*;",
        )
        private val EXPECTED_FILES = setOf(PROGRAM_JAR, CLASSES_DEX, MANIFEST, COMPLETE)
        private fun newAuthenticationKey(): ByteArray = ByteArray(AUTHENTICATION_KEY_BYTES).also {
            SecureRandom().nextBytes(it)
        }
    }
}
