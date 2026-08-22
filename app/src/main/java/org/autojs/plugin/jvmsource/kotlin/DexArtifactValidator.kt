package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import java.security.MessageDigest
import java.util.zip.Adler32

internal data class ValidatedDexArtifact(
    val sizeBytes: Long,
    val sha256: JvmSha256,
    val version: String,
    val classDescriptors: Set<String>,
    val requestMinApi: Int,
    val deviceApi: Int,
    val loaderKind: WorkerDexLoaderKind,
)

/** Strict framing and integrity validation for the single raw classes.dex production profile. */
internal object DexArtifactValidator {
    private const val HEADER_SIZE = 0x70
    private const val ENDIAN_CONSTANT = 0x12345678L
    private const val NO_INDEX = 0xffffffffL
    private const val MAX_MAP_ITEMS = 256L
    private val MAGIC = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte())
    private val CLASS_DESCRIPTOR = Regex(
        "L[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)*;",
    )
    private val D8_SYNTHETIC_DESCRIPTOR = Regex(
        "L[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)*" +
            "\\${'$'}\\${'$'}ExternalSynthetic[A-Za-z0-9_$]*;",
    )

    fun validate(
        bytes: ByteArray,
        expectedSizeBytes: Long,
        expectedSha256: JvmSha256,
        requestMinApi: Int,
        deviceApi: Int,
        expectedClassDescriptors: Set<String>,
    ): ValidatedDexArtifact {
        try {
            require(expectedSizeBytes in HEADER_SIZE.toLong()..JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
            require(bytes.size.toLong() == expectedSizeBytes) { "DEX size differs from compiler metadata" }
            val actualSha256 = JvmSha256.digest(bytes)
            require(actualSha256 == expectedSha256) { "DEX SHA-256 differs from compiler metadata" }
            require(bytes.size >= HEADER_SIZE) { "DEX header is truncated" }
            require(bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "DEX magic is invalid" }
            val version = bytes.copyOfRange(4, 7).toString(Charsets.US_ASCII)
            val admission = DexRuntimePolicy.evaluateVersion(deviceApi, requestMinApi, version)
            require(bytes[7] == 0.toByte() && admission.accepted) {
                "DEX version is outside the device/request API intersection: ${admission.rejection}"
            }
            require(bytes.u32(32) == bytes.size.toLong()) { "DEX file_size is inconsistent" }
            require(bytes.u32(36) == HEADER_SIZE.toLong()) { "DEX header_size is inconsistent" }
            require(bytes.u32(40) == ENDIAN_CONSTANT) { "DEX endian tag is unsupported" }
            require(bytes.u32(44) == 0L && bytes.u32(48) == 0L) { "Linked DEX data is forbidden" }
            validateIntegrity(bytes)

            val mapOffset = bytes.u32(52)
            val sections = listOf(
                Section(TYPE_STRING_ID, bytes.u32(56), bytes.u32(60), 4),
                Section(TYPE_TYPE_ID, bytes.u32(64), bytes.u32(68), 4),
                Section(TYPE_PROTO_ID, bytes.u32(72), bytes.u32(76), 12),
                Section(TYPE_FIELD_ID, bytes.u32(80), bytes.u32(84), 8),
                Section(TYPE_METHOD_ID, bytes.u32(88), bytes.u32(92), 8),
                Section(TYPE_CLASS_DEF, bytes.u32(96), bytes.u32(100), 32),
            )
            val dataSize = bytes.u32(104)
            val dataOffset = bytes.u32(108)
            require(dataSize > 0L && dataOffset >= HEADER_SIZE && dataOffset % 4L == 0L) {
                "DEX data section is invalid"
            }
            require(checkedAdd(dataOffset, dataSize) == bytes.size.toLong()) {
                "DEX data section does not frame the file"
            }
            require(mapOffset in dataOffset until bytes.size.toLong() && mapOffset % 4L == 0L) {
                "DEX map offset is invalid"
            }
            validateFixedSections(sections, dataOffset, bytes.size)
            val map = validateMap(bytes, mapOffset, sections, dataOffset)
            val descriptors = readClassDescriptors(bytes, sections, map, dataOffset)
            require(expectedClassDescriptors.all(descriptors::contains)) {
                "DEX omits a Kotlin compiler class artifact"
            }
            require(descriptors.all { it in expectedClassDescriptors || D8_SYNTHETIC_DESCRIPTOR.matches(it) }) {
                "DEX contains an unexpected class descriptor"
            }
            return ValidatedDexArtifact(
                sizeBytes = bytes.size.toLong(),
                sha256 = actualSha256,
                version = version,
                classDescriptors = descriptors,
                requestMinApi = requestMinApi,
                deviceApi = deviceApi,
                loaderKind = checkNotNull(admission.loaderKind),
            )
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.ARTIFACT_INVALID,
                JvmSourceFailurePhase.WORKER_START,
                "classes.dex failed strict framing and integrity validation",
                error,
            )
        }
    }

    private fun validateIntegrity(bytes: ByteArray) {
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        require(bytes.u32(8) == checksum) { "DEX Adler-32 checksum is invalid" }
        val signature = MessageDigest.getInstance("SHA-1").run {
            update(bytes, 32, bytes.size - 32)
            digest()
        }
        require(MessageDigest.isEqual(signature, bytes.copyOfRange(12, 32))) {
            "DEX SHA-1 signature is invalid"
        }
    }

    private fun validateFixedSections(sections: List<Section>, dataOffset: Long, fileSize: Int) {
        var previousEnd = HEADER_SIZE.toLong()
        sections.forEach { section ->
            if (section.size == 0L) {
                require(section.offset == 0L) { "Empty DEX section has a non-zero offset" }
            } else {
                require(section.offset % 4L == 0L && section.offset >= previousEnd) {
                    "DEX fixed sections overlap or are misaligned"
                }
                val end = checkedAdd(section.offset, checkedMultiply(section.size, section.itemBytes.toLong()))
                require(end <= dataOffset && end <= fileSize.toLong()) { "DEX fixed section exceeds its range" }
                previousEnd = end
            }
        }
    }

    private fun validateMap(
        bytes: ByteArray,
        mapOffset: Long,
        fixedSections: List<Section>,
        dataOffset: Long,
    ): Map<Int, MapItem> {
        val start = mapOffset.toIntExact()
        val count = bytes.u32(start)
        require(count in 1L..MAX_MAP_ITEMS) { "DEX map count is invalid" }
        require(checkedAdd(mapOffset, checkedAdd(4L, checkedMultiply(count, 12L))) <= bytes.size) {
            "DEX map exceeds the file"
        }
        val result = LinkedHashMap<Int, MapItem>()
        var previousOffset = -1L
        repeat(count.toInt()) { index ->
            val cursor = start + 4 + index * 12
            val type = bytes.u16(cursor)
            val unused = bytes.u16(cursor + 2)
            val size = bytes.u32(cursor + 4)
            val offset = bytes.u32(cursor + 8)
            require(type in KNOWN_MAP_TYPES && unused == 0 && size > 0L && result[type] == null) {
                "DEX map item is unknown, empty, or duplicated"
            }
            require(offset > previousOffset && offset < bytes.size) { "DEX map offsets are not ordered" }
            val alignment = if (type in BYTE_ALIGNED_MAP_TYPES) 1L else 4L
            require(offset % alignment == 0L) { "DEX map item is misaligned" }
            if (type in DATA_MAP_TYPES) require(offset >= dataOffset) { "DEX data map item precedes data" }
            result[type] = MapItem(size, offset)
            previousOffset = offset
        }
        require(result[TYPE_HEADER] == MapItem(1L, 0L)) { "DEX map omits its header" }
        require(result[TYPE_MAP_LIST] == MapItem(1L, mapOffset)) { "DEX map does not describe itself" }
        fixedSections.forEach { section ->
            if (section.size == 0L) {
                require(result[section.type] == null) { "DEX map invents an empty fixed section" }
            } else {
                require(result[section.type] == MapItem(section.size, section.offset)) {
                    "DEX map disagrees with a fixed section"
                }
            }
        }
        return result
    }

    private fun readClassDescriptors(
        bytes: ByteArray,
        sections: List<Section>,
        map: Map<Int, MapItem>,
        dataOffset: Long,
    ): Set<String> {
        val strings = sections.single { it.type == TYPE_STRING_ID }
        val types = sections.single { it.type == TYPE_TYPE_ID }
        val classes = sections.single { it.type == TYPE_CLASS_DEF }
        require(types.size <= 65_535L && classes.size <= types.size) { "DEX type/class count is invalid" }
        var previousTypeString = -1L
        repeat(types.size.toInt()) { index ->
            val stringIndex = bytes.u32(types.offset.toIntExact() + index * 4)
            require(stringIndex < strings.size && stringIndex > previousTypeString) { "DEX type IDs are invalid" }
            previousTypeString = stringIndex
        }
        val stringData = requireNotNull(map[TYPE_STRING_DATA]) { "DEX map omits string data" }
        val descriptors = linkedSetOf<String>()
        var previousClassIndex = -1L
        repeat(classes.size.toInt()) { index ->
            val cursor = classes.offset.toIntExact() + index * 32
            val classIndex = bytes.u32(cursor)
            require(classIndex < types.size && classIndex > previousClassIndex) { "DEX class definitions are invalid" }
            previousClassIndex = classIndex
            val descriptorIndex = bytes.u32(types.offset.toIntExact() + classIndex.toInt() * 4)
            val stringOffset = bytes.u32(strings.offset.toIntExact() + descriptorIndex.toInt() * 4)
            require(stringOffset >= dataOffset && stringOffset >= stringData.offset && stringOffset < bytes.size) {
                "DEX class descriptor offset is invalid"
            }
            val descriptor = bytes.readAsciiDexString(stringOffset.toIntExact())
            require(CLASS_DESCRIPTOR.matches(descriptor)) { "DEX class descriptor is outside the R1 profile" }
            require(descriptors.add(descriptor)) { "DEX class descriptor is duplicated" }
        }
        return descriptors
    }

    private data class Section(val type: Int, val size: Long, val offset: Long, val itemBytes: Int)
    private data class MapItem(val size: Long, val offset: Long)

    private val KNOWN_MAP_TYPES = setOf(
        0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007, 0x0008,
        0x1000, 0x1001, 0x1002, 0x1003,
        0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0xf000,
    )
    private val DATA_MAP_TYPES = KNOWN_MAP_TYPES.filterTo(hashSetOf()) { it >= 0x1000 }
    private val BYTE_ALIGNED_MAP_TYPES = setOf(0x2000, 0x2002, 0x2003, 0x2004, 0x2005, 0xf000)

    private const val TYPE_HEADER = 0x0000
    private const val TYPE_STRING_ID = 0x0001
    private const val TYPE_TYPE_ID = 0x0002
    private const val TYPE_PROTO_ID = 0x0003
    private const val TYPE_FIELD_ID = 0x0004
    private const val TYPE_METHOD_ID = 0x0005
    private const val TYPE_CLASS_DEF = 0x0006
    private const val TYPE_MAP_LIST = 0x1000
    private const val TYPE_STRING_DATA = 0x2002
}

private fun ByteArray.u16(offset: Int): Int {
    require(offset >= 0 && offset.toLong() + 2L <= size) { "DEX uint16 exceeds the file" }
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}

private fun ByteArray.u32(offset: Int): Long {
    require(offset >= 0 && offset.toLong() + 4L <= size) { "DEX uint32 exceeds the file" }
    return (this[offset].toLong() and 0xffL) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
}

private fun ByteArray.readAsciiDexString(offset: Int): String {
    var cursor = offset
    var shift = 0
    var utf16Length = 0
    do {
        require(cursor < size && shift <= 28) { "DEX string length is malformed" }
        val byte = this[cursor++].toInt() and 0xff
        utf16Length = utf16Length or ((byte and 0x7f) shl shift)
        shift += 7
    } while (byte and 0x80 != 0)
    require(utf16Length in 1..512) { "DEX class descriptor length is invalid" }
    val result = StringBuilder(utf16Length)
    while (true) {
        require(cursor < size) { "DEX class descriptor is truncated" }
        val byte = this[cursor++].toInt() and 0xff
        if (byte == 0) break
        require(byte in 0x01..0x7f && result.length < 512) {
            "DEX class descriptor is not canonical ASCII"
        }
        result.append(byte.toChar())
    }
    require(result.length == utf16Length) { "DEX class descriptor length disagrees with its data" }
    return result.toString()
}

private fun checkedAdd(left: Long, right: Long): Long {
    require(left >= 0L && right >= 0L && left <= Long.MAX_VALUE - right) { "DEX size addition overflows" }
    return left + right
}

private fun checkedMultiply(left: Long, right: Long): Long {
    require(left >= 0L && right >= 0L && (left == 0L || right <= Long.MAX_VALUE / left)) {
        "DEX size multiplication overflows"
    }
    return left * right
}

private fun Long.toIntExact(): Int {
    require(this in 0L..Int.MAX_VALUE.toLong()) { "DEX offset cannot be represented safely" }
    return toInt()
}
