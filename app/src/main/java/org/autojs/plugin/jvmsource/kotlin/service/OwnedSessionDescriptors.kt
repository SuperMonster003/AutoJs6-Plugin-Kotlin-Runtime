package org.autojs.plugin.jvmsource.kotlin.service

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class OwnedSessionDescriptors private constructor(
    val source: ParcelFileDescriptor,
    private val stdout: ParcelFileDescriptor,
    private val stderr: ParcelFileDescriptor,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val outputsClaimed = AtomicBoolean(false)

    fun duplicateOutputsForWorker(): Pair<ParcelFileDescriptor, ParcelFileDescriptor> {
        check(outputsClaimed.compareAndSet(false, true)) { "Output descriptors may only be transferred once" }
        var stdoutCopy: ParcelFileDescriptor? = null
        var stderrCopy: ParcelFileDescriptor? = null
        try {
            stdoutCopy = ParcelFileDescriptor.dup(stdout.fileDescriptor)
            stderrCopy = ParcelFileDescriptor.dup(stderr.fileDescriptor)
            closeQuietly(stdout)
            closeQuietly(stderr)
            return checkNotNull(stdoutCopy) to checkNotNull(stderrCopy)
        } catch (error: Throwable) {
            closeQuietly(stdoutCopy)
            closeQuietly(stderrCopy)
            throw error
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeQuietly(source)
        closeQuietly(stdout)
        closeQuietly(stderr)
    }

    companion object {
        fun duplicateBeforeAsync(
            source: ParcelFileDescriptor,
            stdout: ParcelFileDescriptor,
            stderr: ParcelFileDescriptor,
        ): OwnedSessionDescriptors {
            var sourceCopy: ParcelFileDescriptor? = null
            var stdoutCopy: ParcelFileDescriptor? = null
            var stderrCopy: ParcelFileDescriptor? = null
            try {
                sourceCopy = ParcelFileDescriptor.dup(source.fileDescriptor)
                stdoutCopy = ParcelFileDescriptor.dup(stdout.fileDescriptor)
                stderrCopy = ParcelFileDescriptor.dup(stderr.fileDescriptor)
                return OwnedSessionDescriptors(sourceCopy, stdoutCopy, stderrCopy)
            } catch (error: Throwable) {
                closeQuietly(sourceCopy)
                closeQuietly(stdoutCopy)
                closeQuietly(stderrCopy)
                throw error
            } finally {
                closeIncoming(source, stdout, stderr)
            }
        }

        fun closeIncoming(vararg descriptors: ParcelFileDescriptor?) {
            descriptors.forEach(::closeQuietly)
        }

        private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
            runCatching { descriptor?.close() }
        }
    }
}
