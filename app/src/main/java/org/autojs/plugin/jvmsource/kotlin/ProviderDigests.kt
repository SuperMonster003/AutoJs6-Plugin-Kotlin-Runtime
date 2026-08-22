package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class ProviderFileIdentity(
    val name: String,
    val sizeBytes: Long,
    val sha256: JvmSha256,
)

internal object ProviderDigests {
    fun file(file: File, maximumBytes: Long = Long.MAX_VALUE): ProviderFileIdentity {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                size = Math.addExact(size, read.toLong())
                require(size <= maximumBytes) { "File exceeds its bounded digest limit: ${file.name}" }
                digest.update(buffer, 0, read)
            }
        }
        return ProviderFileIdentity(file.name, size, JvmSha256.fromBytes(digest.digest()))
    }

    fun combine(domain: String, identities: Collection<ProviderFileIdentity>): JvmSha256 {
        val digest = MessageDigest.getInstance("SHA-256")

        fun add(bytes: ByteArray) {
            digest.update(
                ByteBuffer.allocate(Int.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(bytes.size)
                    .array(),
            )
            digest.update(bytes)
        }

        fun add(value: String) = add(value.toByteArray(Charsets.UTF_8))

        add(domain)
        identities.sortedBy(ProviderFileIdentity::name).forEach { identity ->
            add(identity.name)
            add(identity.sizeBytes.toString())
            add(identity.sha256.toByteArray())
        }
        return JvmSha256.fromBytes(digest.digest())
    }
}
