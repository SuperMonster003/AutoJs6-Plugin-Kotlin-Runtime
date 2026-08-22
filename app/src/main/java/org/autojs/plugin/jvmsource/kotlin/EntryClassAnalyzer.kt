package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException

internal object EntryClassAnalyzer {
    private const val CLASS_MAGIC = 0xcafebabe.toInt()
    private const val MIN_CLASS_VERSION = 45
    private const val JAVA_8_CLASS_VERSION = 52
    private const val ACC_PUBLIC = 0x0001
    private const val ACC_INTERFACE = 0x0200
    private const val ACC_ABSTRACT = 0x0400
    private const val ENTRY_INTERFACE = "org/autojs/plugin/jvmsource/api/AutoJsJvmEntry"

    fun requireSingleEntry(
        classesDirectory: File,
        files: Collection<File>,
        entryClassName: String,
    ): Set<String> {
        val root = classesDirectory.canonicalFile
        val headers = try {
            files.associate { file ->
                val relativeName = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
                    .removeSuffix(".class")
                val header = parse(file)
                if (header.name != relativeName) {
                    throw IOException("Class name does not match its private output path")
                }
                header.name to header
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: Throwable) {
            throw failure(
                JvmSourceErrorCode.ARTIFACT_INVALID,
                "Kotlin compiler produced an invalid JVM class artifact",
                error,
            )
        }
        return requireSingleEntry(headers, files.size, entryClassName)
    }

    /** Rebuilds the trusted entry-ABI summary from bounded cached class bytes. */
    fun requireSingleEntry(
        classFiles: Map<String, ByteArray>,
        entryClassName: String,
    ): Set<String> {
        val headers = try {
            classFiles.mapValues { (name, bytes) ->
                parse(DataInputStream(ByteArrayInputStream(bytes).buffered())).also { header ->
                    if (header.name != name) {
                        throw IOException("Class name does not match its cached JAR entry")
                    }
                }
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: Throwable) {
            throw failure(
                JvmSourceErrorCode.ARTIFACT_INVALID,
                "Cached JAR contains an invalid JVM class artifact",
                error,
            )
        }
        return requireSingleEntry(headers, classFiles.size, entryClassName)
    }

    private fun requireSingleEntry(
        headers: Map<String, ClassHeader>,
        expectedCount: Int,
        entryClassName: String,
    ): Set<String> {
        if (headers.size != expectedCount) {
            throw failure(JvmSourceErrorCode.ARTIFACT_INVALID, "Kotlin compiler produced duplicate JVM class names")
        }
        if (headers.keys.any { !CLASS_INTERNAL_NAME.matches(it) }) {
            throw failure(
                JvmSourceErrorCode.ARTIFACT_INVALID,
                "Java class output contains an invalid package or class name",
            )
        }

        val assignability = HashMap<String, Boolean>()
        fun implementsEntry(name: String, visiting: MutableSet<String>): Boolean {
            assignability[name]?.let { return it }
            val header = headers[name] ?: return name == ENTRY_INTERFACE
            if (!visiting.add(name)) {
                throw failure(JvmSourceErrorCode.ARTIFACT_INVALID, "Kotlin class hierarchy contains a cycle")
            }
            val result = header.interfaces.any { it == ENTRY_INTERFACE || implementsEntry(it, visiting) } ||
                header.superName?.let { implementsEntry(it, visiting) } == true
            visiting.remove(name)
            assignability[name] = result
            return result
        }

        val entryInternalName = entryClassName.replace('.', '/')
        if (!CLASS_INTERNAL_NAME.matches(entryInternalName)) {
            throw failure(JvmSourceErrorCode.ARTIFACT_INVALID, "Requested entry class name is invalid")
        }
        val entry = headers[entryInternalName]
        if (entry == null || !implementsEntry(entry.name, HashSet())) {
            throw failure(
                JvmSourceErrorCode.ENTRY_POINT_MISSING,
                "Requested entry class must implement AutoJsJvmEntry",
            )
        }
        if (entry.accessFlags and (ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) != ACC_PUBLIC ||
            !entry.hasPublicNoArgConstructor
        ) {
            throw failure(
                JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
                "Entry class must be concrete and public with a public no-argument constructor",
            )
        }

        val candidates = headers.values.filter { header ->
            header.accessFlags and (ACC_INTERFACE or ACC_ABSTRACT) == 0 &&
                implementsEntry(header.name, HashSet())
        }
        if (candidates.size != 1) {
            throw failure(
                JvmSourceErrorCode.ENTRY_POINT_AMBIGUOUS,
                "Kotlin source must produce exactly one concrete AutoJsJvmEntry",
            )
        }
        return headers.keys.toSortedSet()
    }

    private fun parse(file: File): ClassHeader = DataInputStream(file.inputStream().buffered()).use(::parse)

    private fun parse(input: DataInputStream): ClassHeader {
        if (input.readInt() != CLASS_MAGIC) throw IOException("Invalid JVM class magic")
        input.readUnsignedShort()
        val majorVersion = input.readUnsignedShort()
        if (majorVersion !in MIN_CLASS_VERSION..JAVA_8_CLASS_VERSION) {
            throw IOException("JVM class version is outside the Java 8 contract")
        }

        val constantPoolCount = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(constantPoolCount)
        val classNameIndexes = IntArray(constantPoolCount)
        var index = 1
        while (index < constantPoolCount) {
            when (input.readUnsignedByte()) {
                1 -> utf8[index] = input.readUTF()
                3, 4 -> input.readInt()
                5, 6 -> {
                    input.readLong()
                    index++
                }
                7 -> classNameIndexes[index] = input.readUnsignedShort()
                8, 16, 19, 20 -> input.readUnsignedShort()
                9, 10, 11, 12, 17, 18 -> input.readInt()
                15 -> {
                    input.readUnsignedByte()
                    input.readUnsignedShort()
                }
                else -> throw IOException("Unknown JVM constant-pool tag")
            }
            index++
        }

        fun className(classIndex: Int): String {
            if (classIndex <= 0 || classIndex >= classNameIndexes.size) {
                throw IOException("Invalid JVM class reference")
            }
            val nameIndex = classNameIndexes[classIndex]
            return utf8.getOrNull(nameIndex) ?: throw IOException("Invalid JVM class name")
        }

        val accessFlags = input.readUnsignedShort()
        val name = className(input.readUnsignedShort())
        val superIndex = input.readUnsignedShort()
        val superName = if (superIndex == 0) null else className(superIndex)
        val interfaces = List(input.readUnsignedShort()) { className(input.readUnsignedShort()) }

        repeat(input.readUnsignedShort()) { skipMember(input) }
        var hasPublicNoArgConstructor = false
        repeat(input.readUnsignedShort()) {
            val methodAccess = input.readUnsignedShort()
            val methodName = utf8.getOrNull(input.readUnsignedShort())
                ?: throw IOException("Invalid JVM method name")
            val methodDescriptor = utf8.getOrNull(input.readUnsignedShort())
                ?: throw IOException("Invalid JVM method descriptor")
            if (methodName == "<init>" && methodDescriptor == "()V" && methodAccess and ACC_PUBLIC != 0) {
                hasPublicNoArgConstructor = true
            }
            skipAttributes(input)
        }
        skipAttributes(input)
        if (input.read() >= 0) throw IOException("JVM class artifact has trailing bytes")
        return ClassHeader(name, superName, interfaces, accessFlags, hasPublicNoArgConstructor)
    }

    private fun skipMember(input: DataInputStream) {
        input.readUnsignedShort()
        input.readUnsignedShort()
        input.readUnsignedShort()
        skipAttributes(input)
    }

    private fun skipAttributes(input: DataInputStream) {
        repeat(input.readUnsignedShort()) {
            input.readUnsignedShort()
            val length = input.readInt()
            if (length < 0) throw IOException("Invalid JVM attribute length")
            skipFully(input, length)
        }
    }

    private fun skipFully(input: DataInputStream, byteCount: Int) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = input.skipBytes(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) throw EOFException("Truncated JVM class artifact")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun failure(
        code: JvmSourceErrorCode,
        message: String,
        cause: Throwable? = null,
    ) = JavaProviderFailure(code, JvmSourceFailurePhase.COMPILATION, message, cause)

    private data class ClassHeader(
        val name: String,
        val superName: String?,
        val interfaces: List<String>,
        val accessFlags: Int,
        val hasPublicNoArgConstructor: Boolean,
    )

    private val CLASS_INTERNAL_NAME = Regex(
        "[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)*",
    )
}
