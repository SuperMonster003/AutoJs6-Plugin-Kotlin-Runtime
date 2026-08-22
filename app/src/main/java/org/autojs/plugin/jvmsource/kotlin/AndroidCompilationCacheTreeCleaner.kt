package org.autojs.plugin.jvmsource.kotlin

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.IOException

/**
 * API 24 fd-pinned, flat cache cleanup. Parent/candidate path replacement can cause a residual
 * entry or denial of service, but cleanup never follows a replaced parent or recursively descends.
 */
internal object AndroidCompilationCacheTreeCleaner : CompilationCacheTreeCleaner {
    override fun deleteFlatTree(
        candidate: File,
        expectedParent: File,
        allowedFileNames: Set<String>,
    ): Boolean {
        val name = candidate.name
        require(name.isNotEmpty() && '/' !in name && '\\' !in name)
        val root = openPinnedDirectory(expectedParent.absolutePath)
        root.use { rootHandle ->
            val rootPath = procPath(rootHandle)
            val candidatePath = "$rootPath/$name"
            val initial = runCatching { Os.lstat(candidatePath) }.getOrNull() ?: return true
            if (!OsConstants.S_ISDIR(initial.st_mode)) {
                Os.remove(candidatePath)
                return runCatching { Os.lstat(candidatePath) }.isFailure
            }

            val entry = openPinnedDirectory(candidatePath)
            entry.use { entryHandle ->
                val entryStat = Os.fstat(entryHandle.fileDescriptor)
                val entryPath = procPath(entryHandle)
                val names = File(entryPath).list()
                    ?: throw IOException("Unable to enumerate pinned compilation cache entry")
                if (names.any { it !in allowedFileNames }) return false
                names.forEach { childName ->
                    val child = Os.lstat("$entryPath/$childName")
                    if (!OsConstants.S_ISREG(child.st_mode)) return false
                }
                names.forEach { childName -> Os.remove("$entryPath/$childName") }

                val current = runCatching { Os.lstat(candidatePath) }.getOrNull() ?: return true
                if (!OsConstants.S_ISDIR(current.st_mode) ||
                    !CompilationCachePathAnchorPolicy.unchanged(
                        entryStat.st_dev,
                        entryStat.st_ino,
                        current.st_dev,
                        current.st_ino,
                    )
                ) return false
                Os.remove(candidatePath)
                return runCatching { Os.lstat(candidatePath) }.isFailure
            }
        }
    }

    private fun openPinnedDirectory(path: String): ParcelFileDescriptor {
        val descriptor: FileDescriptor = Os.open(
            path,
            OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,
            0,
        )
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) {
                throw IOException("Compilation cache cleanup anchor is not an ordinary directory")
            }
            return ParcelFileDescriptor.dup(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun procPath(descriptor: ParcelFileDescriptor): String = "/proc/self/fd/${descriptor.fd}"
}
