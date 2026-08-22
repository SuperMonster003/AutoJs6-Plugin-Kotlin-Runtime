package org.autojs.plugin.jvmsource.kotlin

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/**
 * Resolves a framework-owned private directory before provider-owned children are derived from it.
 * Android may expose a trusted ancestor alias such as /data/user/0 while canonical paths use
 * /data/data. The framework directory itself must still be a real directory, never a final symlink.
 */
internal object AndroidPrivateDirectoryAnchor {
    fun cache(context: Context): File = canonicalizeFrameworkDirectory(
        context.cacheDir,
        "Android cache directory",
    )

    fun codeCache(context: Context): File = canonicalizeFrameworkDirectory(
        context.codeCacheDir,
        "Android code-cache directory",
    )

    internal fun canonicalizeFrameworkDirectory(
        frameworkDirectory: File,
        label: String,
        readOrdinaryDirectoryIdentityWithoutFollowingFinalLink: (File) -> DirectoryIdentity? =
            ::readOrdinaryDirectoryIdentityWithoutFollowingFinalLink,
    ): File {
        val lexical = frameworkDirectory.absoluteFile
        val lexicalIdentity = readOrdinaryDirectoryIdentityWithoutFollowingFinalLink(lexical)
        if (lexicalIdentity == null) {
            throw IOException("$label must be an ordinary private directory")
        }
        val canonical = lexical.canonicalFile
        val canonicalIdentity = readOrdinaryDirectoryIdentityWithoutFollowingFinalLink(canonical)
        if (canonicalIdentity == null || canonicalIdentity != lexicalIdentity) {
            throw IOException("$label changed identity during canonicalization")
        }
        return canonical
    }

    private fun readOrdinaryDirectoryIdentityWithoutFollowingFinalLink(directory: File): DirectoryIdentity? = try {
        val attributes = Os.lstat(directory.path)
        if (OsConstants.S_ISDIR(attributes.st_mode)) {
            DirectoryIdentity(attributes.st_dev, attributes.st_ino)
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    internal data class DirectoryIdentity(val device: Long, val inode: Long)
}
