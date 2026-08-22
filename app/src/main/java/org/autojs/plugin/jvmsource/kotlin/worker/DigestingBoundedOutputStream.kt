package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.JvmSha256
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

internal data class OutputSnapshot(
    val sizeBytes: Long,
    val sha256: JvmSha256,
)

internal class DigestingBoundedOutputStream(
    private val delegate: OutputStream,
    private val maximumBytes: Long,
) : OutputStream() {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val closed = AtomicBoolean(false)
    private var count = 0L
    private var finalSnapshot: OutputSnapshot? = null

    @Volatile
    var overflowed: Boolean = false
        private set

    init { require(maximumBytes >= 0L) }

    @Synchronized
    override fun write(value: Int) {
        ensureOpen()
        if (count >= maximumBytes) return overflow()
        delegate.write(value)
        digest.update(value.toByte())
        count++
    }

    @Synchronized
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > buffer.size - length) {
            throw IndexOutOfBoundsException("Invalid output range")
        }
        ensureOpen()
        if (length == 0) return
        if (count > maximumBytes - length.toLong()) return overflow()
        delegate.write(buffer, offset, length)
        digest.update(buffer, offset, length)
        count += length
    }

    @Synchronized
    override fun flush() {
        ensureOpen()
        delegate.flush()
    }

    @Synchronized
    override fun close() {
        if (closed.compareAndSet(false, true)) delegate.close()
    }

    @Synchronized
    fun snapshot(): OutputSnapshot {
        check(closed.get()) { "Output stream must be closed before its digest is finalized" }
        return finalSnapshot ?: OutputSnapshot(count, JvmSha256.fromBytes(digest.digest())).also {
            finalSnapshot = it
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw IOException("Output stream is closed")
    }

    private fun overflow(): Nothing {
        overflowed = true
        throw IOException("Output exceeds its negotiated byte limit")
    }
}
