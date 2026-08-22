package org.autojs.plugin.jvmsource.kotlin

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * API 24 production writer: exclusive no-follow creation with read-only mode before byte one.
 *
 * Android's public API has no fd-relative openat boundary. We pin a pre/post-open parent
 * device/inode identity and fail on any observed replacement. O_EXCL prevents overwriting an
 * existing target, and callers must use no-follow cleanup. An adversarial undetected ABA parent
 * rename can at worst leave a fresh read-only stray file; this writer does not claim a durable
 * directory capability and never performs recursive cleanup itself.
 */
internal object AndroidCompilationCacheFileWriter : CompilationCacheFileWriter {
    override fun openFreshReadOnly(file: File): CompilationCacheFileWriter.Output {
        val parent = file.absoluteFile.parentFile
            ?: throw IOException("Compilation cache output has no parent")
        val parentBefore = Os.lstat(parent.absolutePath)
        if (!OsConstants.S_ISDIR(parentBefore.st_mode)) {
            throw IOException("Compilation cache output parent is not an ordinary directory")
        }
        val descriptor = Os.open(
            file.absolutePath,
            OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW,
            OsConstants.S_IRUSR,
        )
        var stream: FileOutputStream? = null
        try {
            val parentAfter = Os.lstat(parent.absolutePath)
            if (!OsConstants.S_ISDIR(parentAfter.st_mode) ||
                !CompilationCachePathAnchorPolicy.unchanged(
                    parentBefore.st_dev,
                    parentBefore.st_ino,
                    parentAfter.st_dev,
                    parentAfter.st_ino,
                )
            ) {
                throw IOException("Compilation cache output parent changed during creation")
            }
            val stat = Os.fstat(descriptor)
            val writeBits = OsConstants.S_IWUSR or OsConstants.S_IWGRP or OsConstants.S_IWOTH
            if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_mode and writeBits != 0) {
                throw IOException("Compilation cache output is not a read-only ordinary file")
            }
            val output = FileOutputStream(descriptor).also { stream = it }
            return object : CompilationCacheFileWriter.Output {
                override fun write(bytes: ByteArray, offset: Int, count: Int) = output.write(bytes, offset, count)
                override fun sync() = output.fd.sync()
                override fun close() = output.close()
            }
        } catch (error: Throwable) {
            if (stream == null) runCatching { Os.close(descriptor) } else runCatching { stream.close() }
            throw error
        }
    }
}
