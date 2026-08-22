package org.autojs.plugin.jvmsource.kotlin

import java.io.File
import java.io.IOException

internal fun interface CompilationCacheTreeCleaner {
    /** Returns true only when the named candidate is verified absent from the expected parent. */
    fun deleteFlatTree(candidate: File, expectedParent: File, allowedFileNames: Set<String>): Boolean
}

/** JVM-test fallback. Production Android uses fd-pinned cleanup instead of path-recursive delete. */
internal object JvmCompilationCacheTreeCleaner : CompilationCacheTreeCleaner {
    override fun deleteFlatTree(
        candidate: File,
        expectedParent: File,
        allowedFileNames: Set<String>,
    ): Boolean {
        val lexicalParent = expectedParent.absoluteFile
        val canonicalParent = runCatching { lexicalParent.canonicalFile }.getOrNull() ?: return false
        if (canonicalParent.path != lexicalParent.path || !canonicalParent.isDirectory) return false
        val lexicalCandidate = File(lexicalParent, candidate.name).absoluteFile
        if (candidate.absoluteFile.path != lexicalCandidate.path) return false
        if (lexicalParent.list()?.any { it == candidate.name } != true) return true

        val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull()
        if (canonicalCandidate == null || canonicalCandidate.path != lexicalCandidate.path) {
            // File.delete removes the listed link itself and does not traverse its target.
            return candidate.delete() && lexicalParent.list()?.none { it == candidate.name } == true
        }
        if (!candidate.isDirectory) {
            return candidate.delete() && lexicalParent.list()?.none { it == candidate.name } == true
        }

        val children = candidate.listFiles() ?: return false
        if (children.map(File::getName).any { it !in allowedFileNames } ||
            children.any { child ->
                val expected = File(lexicalCandidate, child.name).absoluteFile
                child.absoluteFile.path != expected.path ||
                    runCatching { child.canonicalFile.path }.getOrNull() != expected.path ||
                    !child.isFile
            }
        ) return false

        children.forEach { child ->
            if (NEEDS_WRITABLE_DELETE && !child.canWrite()) child.setWritable(true, true)
            if (child.parentFile?.list()?.any { it == child.name } == true && !child.delete()) {
                throw IOException("Unable to remove compilation cache file")
            }
        }
        if (candidate.list()?.isNotEmpty() != false) return false
        if (!candidate.delete()) throw IOException("Unable to remove compilation cache directory")
        return lexicalParent.list()?.none { it == candidate.name } == true
    }

    private val NEEDS_WRITABLE_DELETE = File.separatorChar == '\\'
}
