package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal object UserClassJarWriter {
    internal const val MAX_CLASS_FILES = 256
    internal const val MAX_CLASS_BYTES = 16L * 1024L * 1024L
    private const val RESERVED_API_PREFIX = "org/autojs/plugin/jvmsource/api/"

    fun write(
        classesDirectory: File,
        destination: File,
        entryClassName: String = "Main",
    ): UserClassJarSummary {
        val canonicalRoot = classesDirectory.canonicalFile
        val files = canonicalRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "class" }
            .sortedBy { it.relativeTo(canonicalRoot).invariantSeparatorsPath }
            .toList()
        if (files.isEmpty() || files.size > MAX_CLASS_FILES) {
            throw failure("Kotlin/JVM compiler produced an invalid number of class files")
        }
        val classNames = EntryClassAnalyzer.requireSingleEntry(canonicalRoot, files, entryClassName)
        val entryPath = entryClassName.replace('.', '/') + ".class"

        var totalBytes = 0L
        var hasEntryClass = false
        JarOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { output ->
            files.forEach { file ->
                val canonicalFile = file.canonicalFile
                val prefix = canonicalRoot.path + File.separator
                if (!canonicalFile.path.startsWith(prefix)) {
                    throw failure("Kotlin class output escaped its private directory")
                }
                val entryName = canonicalFile.relativeTo(canonicalRoot).invariantSeparatorsPath
                if (entryName.startsWith(RESERVED_API_PREFIX)) {
                    throw failure("User output attempts to replace the entry API")
                }
                if (entryName == entryPath) hasEntryClass = true
                totalBytes = Math.addExact(totalBytes, canonicalFile.length())
                if (totalBytes > MAX_CLASS_BYTES) throw failure("Kotlin class output exceeds its byte limit")

                output.putNextEntry(JarEntry(entryName).apply { time = 0L })
                canonicalFile.inputStream().buffered().use { it.copyTo(output) }
                output.closeEntry()
            }
        }
        if (!hasEntryClass) {
            destination.delete()
            throw JavaProviderFailure(
                JvmSourceErrorCode.ENTRY_POINT_MISSING,
                JvmSourceFailurePhase.COMPILATION,
                "Kotlin source did not produce the requested entry class",
            )
        }
        return UserClassJarSummary(
            classFileCount = files.size,
            dexDescriptors = classNames.mapTo(sortedSetOf()) { "L$it;" },
        )
    }

    private fun failure(message: String) = JavaProviderFailure(
        JvmSourceErrorCode.COMPILATION_FAILED,
        JvmSourceFailurePhase.COMPILATION,
        message,
    )
}

internal data class UserClassJarSummary(
    val classFileCount: Int,
    val dexDescriptors: Set<String>,
)
