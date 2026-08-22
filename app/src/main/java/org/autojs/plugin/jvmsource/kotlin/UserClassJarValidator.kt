package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Rebuilds the class/entry-ABI summary without trusting cache metadata or ZIP entry sizes. */
internal object UserClassJarValidator {
    private val CLASS_ENTRY = Regex(
        "[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)*\\.class",
    )

    fun validate(
        programJar: File,
        entryClassName: String = "Main",
    ): Pair<ProviderFileIdentity, UserClassJarSummary> {
        val identity = try {
            ProviderDigests.file(programJar, JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES)
        } catch (error: Throwable) {
            throw invalid("Cached class JAR exceeds its framing limit", error)
        }
        val classFiles = linkedMapOf<String, ByteArray>()
        var totalClassBytes = 0L
        try {
            val expectedEntryCount = validateZipFraming(programJar)
            ZipFile(programJar).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(!entry.isDirectory && CLASS_ENTRY.matches(entry.name)) {
                        "Cached class JAR contains a non-class or non-canonical entry"
                    }
                    require(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED) {
                        "Cached class JAR uses an unsupported ZIP method"
                    }
                    require(entry.name.removeSuffix(".class") !in classFiles) {
                        "Cached class JAR contains a duplicate class"
                    }
                    require(classFiles.size < UserClassJarWriter.MAX_CLASS_FILES) {
                        "Cached class JAR contains too many classes"
                    }
                    val bytes = ByteArrayOutputStream().use { output ->
                        zip.getInputStream(entry).buffered().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                totalClassBytes = Math.addExact(totalClassBytes, read.toLong())
                                require(totalClassBytes <= UserClassJarWriter.MAX_CLASS_BYTES) {
                                    "Cached class JAR expands beyond its class-byte limit"
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                        output.toByteArray()
                    }
                    require(entry.size < 0L || entry.size == bytes.size.toLong()) {
                        "Cached class JAR entry size is inconsistent"
                    }
                    classFiles[entry.name.removeSuffix(".class")] = bytes
                }
            }
            require(classFiles.size == expectedEntryCount) { "Cached class JAR entry count is inconsistent" }
            require(classFiles.isNotEmpty()) { "Cached class JAR is empty" }
            val descriptors = EntryClassAnalyzer.requireSingleEntry(classFiles, entryClassName)
                .mapTo(sortedSetOf()) { "L$it;" }
            return identity to UserClassJarSummary(classFiles.size, descriptors)
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: Throwable) {
            throw invalid("Cached class JAR failed strict framing and entry-ABI validation", error)
        }
    }

    private fun invalid(message: String, cause: Throwable? = null) = JavaProviderFailure(
        JvmSourceErrorCode.ARTIFACT_INVALID,
        JvmSourceFailurePhase.COMPILATION,
        message,
        cause,
    )

    private fun validateZipFraming(file: File): Int = RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        require(length in EOCD_MINIMUM_SIZE.toLong()..JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES)
        val tailSize = minOf(length, EOCD_MAXIMUM_SEARCH.toLong()).toInt()
        val tail = ByteArray(tailSize)
        input.seek(length - tailSize)
        input.readFully(tail)
        var eocd = -1
        for (offset in tail.size - EOCD_MINIMUM_SIZE downTo 0) {
            if (tail.u32(offset) == EOCD_SIGNATURE) {
                val commentBytes = tail.u16(offset + 20)
                if (offset + EOCD_MINIMUM_SIZE + commentBytes == tail.size) {
                    eocd = offset
                    break
                }
            }
        }
        require(eocd >= 0) { "Cached class JAR has no terminal central-directory record" }
        require(tail.u16(eocd + 4) == 0 && tail.u16(eocd + 6) == 0) {
            "Cached class JAR is split across disks"
        }
        val diskEntries = tail.u16(eocd + 8)
        val totalEntries = tail.u16(eocd + 10)
        require(diskEntries == totalEntries && totalEntries in 1..UserClassJarWriter.MAX_CLASS_FILES) {
            "Cached class JAR central-directory count is invalid"
        }
        val centralSize = tail.u32(eocd + 12)
        val centralOffset = tail.u32(eocd + 16)
        val absoluteEocd = length - tailSize + eocd
        require(centralOffset + centralSize == absoluteEocd) {
            "Cached class JAR central directory does not exactly frame its local entries"
        }
        totalEntries
    }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.u32(offset: Int): Long =
        (u16(offset).toLong() and 0xffffL) or ((u16(offset + 2).toLong() and 0xffffL) shl 16)

    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val EOCD_MINIMUM_SIZE = 22
    private const val EOCD_MAXIMUM_SEARCH = EOCD_MINIMUM_SIZE + 65_535
}
