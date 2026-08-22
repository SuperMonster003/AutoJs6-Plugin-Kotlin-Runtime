package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal const val JAVA_COMPILATION_CACHE_IMPLEMENTATION_REVISION = "r3-cache-v2"

internal data class CompilationArtifactProvenance(
    val rawSourceSha256: JvmSha256,
    val normalizedSourceSha256: JvmSha256,
    val sourceCharsetPolicy: String,
    val sourceNormalizationPolicy: String,
    val sourceFileName: String,
    val entryClassName: String,
    val language: String,
    val protocolMajor: Int,
    val protocolMinor: Int,
    val entryApiVersion: Int,
    val sourceCompilerFamily: String,
    val sourceCompilerVersion: String,
    val sourceCompilerOptions: List<String>,
    val d8Version: String,
    val d8Options: List<String>,
    val minApi: Int,
    val allowedHostCalls: List<String>,
    val runtimeLibraryFingerprint: JvmSha256,
    val toolchainFingerprint: JvmSha256,
    val providerId: String,
    val providerApiVersionName: String,
    val providerApiVersionCode: Long,
    val providerPackageName: String,
    val providerComponentName: String,
    val providerUid: Int,
    val providerSignerSha256: List<String>,
    val installedVersionCode: Long,
    val installedVersionName: String,
    val installedLastUpdateTime: Long,
    val runtimeApi: Int,
    val supportedAbis: List<String>,
    val vmArchitecture: String,
    val providerImplementationRevision: String = JAVA_COMPILATION_CACHE_IMPLEMENTATION_REVISION,
)

internal data class CompilationArtifactCacheKey(val sha256: JvmSha256) {
    val hex: String
        get() = sha256.toHexString()

    companion object {
        fun compute(value: CompilationArtifactProvenance): CompilationArtifactCacheKey =
            CompilationArtifactCacheKey(
                JvmSha256.fromBytes(
                    MessageDigest.getInstance("SHA-256")
                        .digest(CompilationArtifactCacheKeyCanonicalCodec.encode(value)),
                ),
            )
    }
}

internal enum class CompilationCacheKeyValueType(val wireTag: Int) {
    STRING(1),
    INT32(2),
    INT64(3),
    SHA256(4),
    STRING_LIST(5),
}

/**
 * Stable cache-key schema. Names are UTF-8 length framed; every value carries an explicit type
 * tag and uses fixed big-endian numeric encoding. Lists retain their declared ordering.
 */
internal object CompilationArtifactCacheKeyCanonicalCodec {
    internal const val MAGIC = 0x414a434b // AJCK
    internal const val SCHEMA_VERSION = 2
    private const val DOMAIN = "org.autojs.jvm-source.kotlin-compilation-cache-key.v2"

    fun encode(value: CompilationArtifactProvenance): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            val names = hashSetOf<String>()
            output.writeInt(MAGIC)
            output.writeInt(SCHEMA_VERSION)

            fun field(name: String, type: CompilationCacheKeyValueType, write: () -> Unit) {
                require(names.add(name)) { "Duplicate cache-key field: $name" }
                output.writeFramedUtf8(name)
                output.writeByte(type.wireTag)
                write()
            }

            fun string(name: String, text: String) =
                field(name, CompilationCacheKeyValueType.STRING) { output.writeFramedUtf8(text) }

            fun int32(name: String, number: Int) =
                field(name, CompilationCacheKeyValueType.INT32) { output.writeInt(number) }

            fun int64(name: String, number: Long) =
                field(name, CompilationCacheKeyValueType.INT64) { output.writeLong(number) }

            fun sha256(name: String, hash: JvmSha256) =
                field(name, CompilationCacheKeyValueType.SHA256) { output.write(hash.toByteArray()) }

            fun stringList(name: String, values: List<String>) =
                field(name, CompilationCacheKeyValueType.STRING_LIST) {
                    output.writeInt(values.size)
                    values.forEach { item -> output.writeFramedUtf8(item) }
                }

            string("domain", DOMAIN)
            sha256("rawSourceSha256", value.rawSourceSha256)
            sha256("normalizedSourceSha256", value.normalizedSourceSha256)
            string("sourceCharsetPolicy", value.sourceCharsetPolicy)
            string("sourceNormalizationPolicy", value.sourceNormalizationPolicy)
            string("sourceFileName", value.sourceFileName)
            string("entryClassName", value.entryClassName)
            string("language", value.language)
            int32("protocolMajor", value.protocolMajor)
            int32("protocolMinor", value.protocolMinor)
            int32("entryApiVersion", value.entryApiVersion)
            string("sourceCompilerFamily", value.sourceCompilerFamily)
            string("sourceCompilerVersion", value.sourceCompilerVersion)
            stringList("sourceCompilerOptions", value.sourceCompilerOptions)
            string("d8Version", value.d8Version)
            stringList("d8Options", value.d8Options)
            int32("minApi", value.minApi)
            stringList("allowedHostCalls", value.allowedHostCalls.sorted())
            sha256("runtimeLibraryFingerprint", value.runtimeLibraryFingerprint)
            sha256("toolchainFingerprint", value.toolchainFingerprint)
            string("providerId", value.providerId)
            string("providerImplementationRevision", value.providerImplementationRevision)
            string("providerApiVersionName", value.providerApiVersionName)
            int64("providerApiVersionCode", value.providerApiVersionCode)
            string("providerPackageName", value.providerPackageName)
            string("providerComponentName", value.providerComponentName)
            int32("providerUid", value.providerUid)
            stringList(
                "providerSignerSha256",
                value.providerSignerSha256.map(String::lowercase).distinct().sorted(),
            )
            int64("installedVersionCode", value.installedVersionCode)
            string("installedVersionName", value.installedVersionName)
            int64("installedLastUpdateTime", value.installedLastUpdateTime)
            int32("runtimeApi", value.runtimeApi)
            stringList("supportedAbis", value.supportedAbis)
            string("vmArchitecture", value.vmArchitecture)
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writeFramedUtf8(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        writeInt(encoded.size)
        write(encoded)
    }
}
