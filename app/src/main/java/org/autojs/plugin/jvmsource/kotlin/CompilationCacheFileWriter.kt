package org.autojs.plugin.jvmsource.kotlin

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal interface CompilationCacheFileWriter {
    fun openFreshReadOnly(file: File): Output

    interface Output : Closeable {
        fun write(bytes: ByteArray, offset: Int, count: Int)
        fun sync()
    }
}

/** JVM unit-test fallback; Android production injects the O_EXCL/O_NOFOLLOW implementation. */
internal object JvmCompilationCacheFileWriter : CompilationCacheFileWriter {
    override fun openFreshReadOnly(file: File): CompilationCacheFileWriter.Output {
        if (!file.createNewFile()) throw IOException("Compilation cache file already exists")
        val stream = FileOutputStream(file)
        if (!file.setReadOnly()) {
            stream.close()
            throw IOException("Unable to make compilation cache file read-only")
        }
        return object : CompilationCacheFileWriter.Output {
            override fun write(bytes: ByteArray, offset: Int, count: Int) = stream.write(bytes, offset, count)
            override fun sync() = stream.fd.sync()
            override fun close() = stream.close()
        }
    }
}
