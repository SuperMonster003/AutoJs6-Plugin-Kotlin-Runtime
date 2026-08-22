package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.Adler32

class CompilationArtifactCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun publishesThenRevalidatesBothArtifactsOnEveryHit() {
        val fixture = fixture("hit")
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })

        val published = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        assertEquals(fixture.classIdentity, published.classIdentity)
        assertNotNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
        assertTrue(published.programJar.parentFile.listFiles().orEmpty().all { !it.canWrite() })

        val materializedRoot = temporaryFolder.newFolder("materialized-hit")
        val materialized = cache.materialize(
            cached = published,
            destinationProgramJar = materializedRoot.resolve("program.jar"),
            destinationDexFile = materializedRoot.resolve("classes.dex"),
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        assertEquals(fixture.classIdentity, materialized.classIdentity)
        assertEquals(fixture.dexIdentity, materialized.dexIdentity)

        assertTrue(published.programJar.setWritable(true, true))
        published.programJar.appendBytes(byteArrayOf(1))
        assertNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
        assertFalse(published.programJar.parentFile.exists())
    }

    @Test
    fun packagedEntrySurvivesPublicationLookupAndMaterialization() {
        val entryClassName = "com.example.scripts.Main"
        val fixture = fixture("packaged-hit", entryClassName = entryClassName)
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })

        val published = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            entryClassName = entryClassName,
            ensureActive = {},
        )
        val hit = checkNotNull(cache.lookup(fixture.key, 24, 24, entryClassName))
        val destination = temporaryFolder.newFolder("packaged-materialized")
        val materialized = cache.materialize(
            cached = hit,
            destinationProgramJar = destination.resolve("program.jar"),
            destinationDexFile = destination.resolve("classes.dex"),
            requestMinApi = 24,
            deviceApi = 24,
            entryClassName = entryClassName,
            ensureActive = {},
        )

        assertEquals(published.classSummary, materialized.classSummary)
        assertTrue("Lcom/example/scripts/Main;" in materialized.classSummary.dexDescriptors)
    }

    @Test
    fun expirationAndRuntimeAdmissionAreFailClosedMisses() {
        var now = 10_000L
        val fixture = fixture("expiry")
        val cache = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { now },
            ttlMillis = 100L,
        )
        cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        now += 101L
        assertNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
    }

    @Test
    fun interruptedPublicationLeavesNeitherEntryNorStagingDirectory() {
        val fixture = fixture("cancel")
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })
        var checks = 0

        assertThrows(Stopped::class.java) {
            cache.publish(
                fixture.key,
                fixture.programJar,
                fixture.dexFile,
                fixture.summary,
                fixture.classIdentity,
                fixture.dexIdentity,
                requestMinApi = 24,
                deviceApi = 24,
                ensureActive = { if (++checks >= 3) throw Stopped() },
            )
        }
        assertTrue(fixture.cacheRoot.list().orEmpty().isEmpty())
    }

    @Test
    fun staleSymlinkCleanupNeverFollowsItsTargetWhenSupported() {
        val fixture = fixture("symlink")
        assertTrue(fixture.cacheRoot.mkdirs())
        val outside = temporaryFolder.newFolder("outside-cache-target")
        val protected = outside.resolve("protected.txt").apply { writeText("keep") }
        val link = fixture.cacheRoot.resolve(".publish-00000000-0000-0000-0000-000000000001")
        if (runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isFailure) return

        assertNull(CompilationArtifactCache(fixture.cacheRoot).lookup(fixture.key, 24, 24))

        assertTrue(protected.isFile)
        assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun nestedLinkMakesFlatCleanupFailSafeWithoutFollowingOrRecursing() {
        val fixture = fixture("nested-cleanup-link")
        assertTrue(fixture.cacheRoot.mkdirs())
        val staging = fixture.cacheRoot.resolve(".publish-00000000-0000-0000-0000-000000000002")
            .apply { check(mkdir()) }
        val outside = temporaryFolder.newFolder("outside-nested-cleanup")
        val protected = outside.resolve("protected.txt").apply { writeText("keep") }
        val nested = staging.resolve("program.jar")
        if (runCatching { Files.createSymbolicLink(nested.toPath(), outside.toPath()) }.isFailure) return

        assertNull(CompilationArtifactCache(fixture.cacheRoot).lookup(fixture.key, 24, 24))

        assertTrue(protected.isFile)
        assertTrue(Files.isSymbolicLink(nested.toPath()))
        assertTrue(staging.isDirectory)
    }

    @Test
    fun ordinaryDigestCannotForgeCompilerProcessHmac() {
        val fixture = fixture("hmac-forgery")
        val cache = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_000L },
            authenticationKey = ByteArray(32) { 1 },
        )
        val published = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        val manifest = published.programJar.parentFile.resolve("manifest.bin")
        assertTrue(manifest.setWritable(true, true))
        manifest.appendBytes(byteArrayOf(1))
        val ordinaryDigest = MessageDigest.getInstance("SHA-256").digest(manifest.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val completion = manifest.parentFile.resolve("complete.hmac")
        assertTrue(completion.setWritable(true, true))
        completion.writeText(ordinaryDigest, Charsets.US_ASCII)
        assertTrue(manifest.setReadOnly())
        assertTrue(completion.setReadOnly())

        assertNull(cache.lookup(fixture.key, 24, 24))
    }

    @Test
    fun newCompilerProcessEpochRejectsOldEntries() {
        val fixture = fixture("process-epoch")
        val first = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_000L },
            authenticationKey = ByteArray(32) { 1 },
        )
        first.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )

        val restarted = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_001L },
            authenticationKey = ByteArray(32) { 2 },
        )
        assertNull(restarted.lookup(fixture.key, 24, 24))
        assertTrue(fixture.cacheRoot.list().orEmpty().isEmpty())
    }

    @Test
    fun enforcesCreationOrderEntryQuotaAndHardByteQuota() {
        var now = 1_000L
        val sharedRoot = temporaryFolder.newFolder("quota-root")
        val firstFixture = fixture("quota-first", sharedRoot)
        val secondFixture = fixture("quota-second", sharedRoot)
        val entryBounded = CompilationArtifactCache(
            sharedRoot,
            clockMillis = { now },
            maximumEntries = 1,
        )
        entryBounded.publish(
            firstFixture.key,
            firstFixture.programJar,
            firstFixture.dexFile,
            firstFixture.summary,
            firstFixture.classIdentity,
            firstFixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        now++
        entryBounded.publish(
            secondFixture.key,
            secondFixture.programJar,
            secondFixture.dexFile,
            secondFixture.summary,
            secondFixture.classIdentity,
            secondFixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        assertNull(entryBounded.lookup(firstFixture.key, 24, 24))
        assertNotNull(entryBounded.lookup(secondFixture.key, 24, 24))

        val byteRoot = temporaryFolder.newFolder("byte-quota-root")
        val byteFixture = fixture("byte-quota", byteRoot)
        val byteBounded = CompilationArtifactCache(byteRoot, maximumBytes = 1L)
        assertThrows(IllegalArgumentException::class.java) {
            byteBounded.publish(
                byteFixture.key,
                byteFixture.programJar,
                byteFixture.dexFile,
                byteFixture.summary,
                byteFixture.classIdentity,
                byteFixture.dexIdentity,
                24,
                24,
                ensureActive = {},
            )
        }
        assertTrue(byteRoot.list().orEmpty().isEmpty())
    }

    @Test
    fun unauthenticatedFakeEntriesNeverConsumeQuotaOrEvictAuthenticEntry() {
        val fixture = fixture("fake-quota")
        val cache = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_000L },
            maximumEntries = 1,
            authenticationKey = ByteArray(32) { 1 },
        )
        val authentic = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        val fakeKey = CompilationArtifactCacheKey(JvmSha256.digest("fake-key".toByteArray()))
        val fake = fixture.cacheRoot.resolve("entry-${fakeKey.hex}")
        assertTrue(authentic.programJar.parentFile.copyRecursively(fake))
        fake.listFiles().orEmpty().forEach { assertTrue(it.setReadOnly()) }

        assertNotNull(cache.lookup(fixture.key, 24, 24))
        assertFalse(fake.exists())
    }

    private fun fixture(
        name: String,
        cacheRoot: File? = null,
        entryClassName: String = "Main",
    ): Fixture {
        val root = temporaryFolder.newFolder(name)
        val artifacts = root.resolve("artifacts").apply { check(mkdir()) }
        val programJar = artifacts.resolve("program.jar")
        val packageName = entryClassName.substringBeforeLast('.', missingDelimiterValue = "")
            .ifEmpty { null }
        val main = CacheTestArtifacts.java8MainClass(
            root.resolve("compile").apply { check(mkdir()) },
            packageName,
        )
        val classEntry = entryClassName.replace('.', '/') + ".class"
        JarOutputStream(programJar.outputStream().buffered()).use { output ->
            output.putNextEntry(JarEntry(classEntry).apply { time = 0L })
            output.write(main)
            output.closeEntry()
        }
        val descriptor = "L${entryClassName.replace('.', '/')};"
        val dexFile = artifacts.resolve("classes.dex").apply { writeBytes(minimalDex(descriptor)) }
        val classValidation = UserClassJarValidator.validate(programJar, entryClassName)
        return Fixture(
            cacheRoot = cacheRoot ?: root.resolve("cache"),
            key = CompilationArtifactCacheKey(JvmSha256.digest(name.toByteArray())),
            programJar = programJar,
            dexFile = dexFile,
            summary = classValidation.second,
            classIdentity = classValidation.first,
            dexIdentity = ProviderDigests.file(dexFile),
        )
    }

    private fun minimalDex(descriptor: String = "LMain;"): ByteArray {
        require(descriptor.length < 128 && descriptor.startsWith('L') && descriptor.endsWith(';'))
        val stringOffset = 152
        val stringEnd = stringOffset + 1 + descriptor.length + 1
        val mapOffset = (stringEnd + 3) and -4
        val bytes = ByteArray(mapOffset + 4 + 6 * 12)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        buffer.putInt(32, bytes.size)
        buffer.putInt(36, 0x70)
        buffer.putInt(40, 0x12345678)
        buffer.putInt(52, mapOffset)
        buffer.putInt(56, 1)
        buffer.putInt(60, 112)
        buffer.putInt(64, 1)
        buffer.putInt(68, 116)
        buffer.putInt(96, 1)
        buffer.putInt(100, 120)
        buffer.putInt(104, bytes.size - stringOffset)
        buffer.putInt(108, 152)
        buffer.putInt(112, 152)
        buffer.putInt(116, 0)
        buffer.putInt(120, 0)
        buffer.putInt(124, 1)
        buffer.putInt(128, -1)
        buffer.putInt(136, -1)
        bytes[stringOffset] = descriptor.length.toByte()
        descriptor.toByteArray(Charsets.US_ASCII).copyInto(bytes, stringOffset + 1)
        bytes[stringEnd - 1] = 0
        buffer.putInt(mapOffset, 6)
        var cursor = mapOffset + 4
        fun map(type: Int, size: Int, offset: Int) {
            buffer.putShort(cursor, type.toShort())
            buffer.putShort(cursor + 2, 0)
            buffer.putInt(cursor + 4, size)
            buffer.putInt(cursor + 8, offset)
            cursor += 12
        }
        map(0x0000, 1, 0)
        map(0x0001, 1, 112)
        map(0x0002, 1, 116)
        map(0x0006, 1, 120)
        map(0x2002, 1, stringOffset)
        map(0x1000, 1, mapOffset)
        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        signature.copyInto(bytes, 12)
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        buffer.putInt(8, checksum.toInt())
        return bytes
    }

    private data class Fixture(
        val cacheRoot: File,
        val key: CompilationArtifactCacheKey,
        val programJar: File,
        val dexFile: File,
        val summary: UserClassJarSummary,
        val classIdentity: ProviderFileIdentity,
        val dexIdentity: ProviderFileIdentity,
    )

    private class Stopped : RuntimeException()
}
