package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class CompilationArtifactCacheKeyTest {
    @Test
    fun bindsRawAndNormalizedSourceIdentitiesIndependently() {
        val base = provenance()

        assertNotEquals(key(base), key(base.copy(rawSourceSha256 = hash("raw-bom"))))
        assertNotEquals(key(base), key(base.copy(normalizedSourceSha256 = hash("normalized-other"))))
        assertNotEquals(key(base), key(base.copy(sourceNormalizationPolicy = "future-normalization-v2")))
        assertNotEquals(key(base), key(base.copy(sourceCharsetPolicy = "future-charset-v2")))
    }

    @Test
    fun invalidatesForToolchainProtocolProviderRuntimeAndAbiDrift() {
        val base = provenance()
        val variants = listOf(
            base.copy(protocolMinor = 1),
            base.copy(entryApiVersion = 2),
            base.copy(sourceCompilerVersion = "3.43.0"),
            base.copy(sourceCompilerOptions = base.sourceCompilerOptions + "warnings=error"),
            base.copy(d8Version = "8.14.0"),
            base.copy(minApi = 25),
            base.copy(runtimeLibraryFingerprint = hash("runtime-2")),
            base.copy(toolchainFingerprint = hash("toolchain-2")),
            base.copy(providerComponentName = "pkg/OtherCompiler"),
            base.copy(providerImplementationRevision = "r3-cache-v3"),
            base.copy(providerSignerSha256 = listOf("b".repeat(64))),
            base.copy(installedVersionCode = 2L),
            base.copy(installedLastUpdateTime = 124L),
            base.copy(runtimeApi = 25),
            base.copy(supportedAbis = listOf("x86_64", "arm64-v8a")),
            base.copy(vmArchitecture = "x86_64"),
        )

        variants.forEach { assertNotEquals(key(base), key(it)) }
    }

    @Test
    fun canonicalizesSetLikeSignerAndAllowedCallFieldsOnly() {
        val base = provenance()
        assertEquals(
            key(base.copy(allowedHostCalls = listOf("z", "a"))),
            key(base.copy(allowedHostCalls = listOf("a", "z"))),
        )
        assertEquals(
            key(base.copy(providerSignerSha256 = listOf("A".repeat(64), "b".repeat(64)))),
            key(base.copy(providerSignerSha256 = listOf("b".repeat(64), "a".repeat(64)))),
        )
        assertNotEquals(
            key(base.copy(supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))),
            key(base.copy(supportedAbis = listOf("armeabi-v7a", "arm64-v8a"))),
        )
    }

    @Test
    fun canonicalSchemaCarriesExplicitStableTypeTags() {
        val input = DataInputStream(
            ByteArrayInputStream(CompilationArtifactCacheKeyCanonicalCodec.encode(provenance())),
        )
        assertEquals(CompilationArtifactCacheKeyCanonicalCodec.MAGIC, input.readInt())
        assertEquals(2, CompilationArtifactCacheKeyCanonicalCodec.SCHEMA_VERSION)
        assertEquals(CompilationArtifactCacheKeyCanonicalCodec.SCHEMA_VERSION, input.readInt())
        assertEquals(listOf(1, 2, 3, 4, 5), CompilationCacheKeyValueType.entries.map { it.wireTag })

        val observed = linkedMapOf<String, Int>()
        while (input.available() > 0) {
            val name = input.readFramedUtf8()
            val tag = input.readUnsignedByte()
            assertTrue(observed.put(name, tag) == null)
            when (tag) {
                CompilationCacheKeyValueType.STRING.wireTag -> input.readFramedUtf8()
                CompilationCacheKeyValueType.INT32.wireTag -> input.readInt()
                CompilationCacheKeyValueType.INT64.wireTag -> input.readLong()
                CompilationCacheKeyValueType.SHA256.wireTag -> input.readFully(ByteArray(JvmSha256.BYTE_COUNT))
                CompilationCacheKeyValueType.STRING_LIST.wireTag -> repeat(input.readInt()) {
                    input.readFramedUtf8()
                }
                else -> error("Unknown cache-key type tag $tag")
            }
        }
        assertEquals(CompilationCacheKeyValueType.STRING.wireTag, observed.getValue("domain"))
        assertEquals(CompilationCacheKeyValueType.SHA256.wireTag, observed.getValue("rawSourceSha256"))
        assertEquals(CompilationCacheKeyValueType.INT32.wireTag, observed.getValue("protocolMajor"))
        assertEquals(CompilationCacheKeyValueType.INT64.wireTag, observed.getValue("installedVersionCode"))
        assertEquals(CompilationCacheKeyValueType.STRING_LIST.wireTag, observed.getValue("d8Options"))
    }

    @Test
    fun canonicalSchemaHasAPinnedGoldenDigest() {
        assertEquals(
            "12f053c7e980ec22ae6ff2a3d1cb4857175e5288b2d3e92521e510dc7ceb34dc",
            key(provenance()).hex,
        )
    }

    private fun key(value: CompilationArtifactProvenance) = CompilationArtifactCacheKey.compute(value)

    private fun provenance() = CompilationArtifactProvenance(
        rawSourceSha256 = hash("raw"),
        normalizedSourceSha256 = hash("normalized"),
        sourceCharsetPolicy = "UTF-8-strict-report-v1",
        sourceNormalizationPolicy = "strip-leading-bom-and-reencode-UTF-8-v1",
        sourceFileName = "Main.java",
        entryClassName = "Main",
        language = "java",
        protocolMajor = 1,
        protocolMinor = 0,
        entryApiVersion = 1,
        sourceCompilerFamily = "ecj",
        sourceCompilerVersion = "3.26.0",
        sourceCompilerOptions = listOf("source=8", "target=8"),
        d8Version = "8.13.17",
        d8Options = listOf("mode=debug", "min-api=24"),
        minApi = 24,
        allowedHostCalls = listOf("app.launch"),
        runtimeLibraryFingerprint = hash("runtime"),
        toolchainFingerprint = hash("toolchain"),
        providerId = "ecj-java",
        providerApiVersionName = "0.1.0-r1",
        providerApiVersionCode = 1L,
        providerPackageName = "org.autojs.plugin.jvmsource.kotlin",
        providerComponentName = "org.autojs.plugin.jvmsource.kotlin/.service.JavaSourceCompilerService",
        providerUid = 12345,
        providerSignerSha256 = listOf("a".repeat(64)),
        installedVersionCode = 1L,
        installedVersionName = "0.1.0-r1",
        installedLastUpdateTime = 123L,
        runtimeApi = 24,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        vmArchitecture = "aarch64",
    )

    private fun hash(value: String) = JvmSha256.digest(value.toByteArray())

    private fun DataInputStream.readFramedUtf8(): String {
        val size = readInt()
        require(size >= 0)
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }
}
