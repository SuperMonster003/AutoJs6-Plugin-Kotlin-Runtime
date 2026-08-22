package org.autojs.plugin.jvmsource.kotlin.worker

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Locks the Android 14 dynamic-code write order into one auditable operation:
 * create empty -> open output -> mark read-only -> first content write -> sync.
 */
internal object ReadOnlyDexWritePolicy {
    fun write(bytes: ByteArray, target: Target) {
        require(bytes.isNotEmpty()) { "A dynamic DEX artifact must not be empty" }
        target.createEmpty()
        target.openForWrite().use { output ->
            target.markReadOnly()
            output.writeContent(bytes)
            output.sync()
        }
    }

    interface Target {
        fun createEmpty()

        fun openForWrite(): OpenOutput

        fun markReadOnly()
    }

    interface OpenOutput : Closeable {
        fun writeContent(bytes: ByteArray)

        fun sync()
    }
}

internal class FileReadOnlyDexWriteTarget(private val file: File) : ReadOnlyDexWritePolicy.Target {
    override fun createEmpty() {
        if (!file.createNewFile()) throw IOException("Unable to create worker DEX")
    }

    override fun openForWrite(): ReadOnlyDexWritePolicy.OpenOutput {
        val stream = FileOutputStream(file)
        return object : ReadOnlyDexWritePolicy.OpenOutput {
            override fun writeContent(bytes: ByteArray) {
                stream.write(bytes)
            }

            override fun sync() {
                stream.fd.sync()
            }

            override fun close() {
                stream.close()
            }
        }
    }

    override fun markReadOnly() {
        if (!file.setReadOnly()) throw IOException("Unable to make worker DEX read-only")
    }
}
