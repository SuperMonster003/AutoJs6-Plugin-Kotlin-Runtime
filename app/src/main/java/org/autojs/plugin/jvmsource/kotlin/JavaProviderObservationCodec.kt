package org.autojs.plugin.jvmsource.kotlin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Provider-internal codec. This is not part of the external JVM-source Protocol V1. */
internal object JavaProviderObservationCodec {
    private const val MAGIC = 0x4a564f31 // JVO1
    const val MAX_ENCODED_BYTES = 2_048
    private const val ABSENT_LONG = -1L
    private const val ABSENT_INT = -1

    fun encode(value: JavaProviderObservation): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(value.startProfile.wireCode)
            data.writeNullableLong(value.compileDurationMillis)
            data.writeNullableLong(value.d8DurationMillis)
            data.writeNullableLong(value.loadDurationMillis)
            data.writeNullableLong(value.runDurationMillis)
            data.writeNullableLong(value.terminationDurationMillis)
            data.writeInt(value.cacheOutcome.wireCode)
            data.writeInt(value.cacheMissReason?.wireCode ?: ABSENT_INT)
            data.writeInt(value.resources.size)
            value.resources.forEach { resource ->
                data.writeInt(resource.process.wireCode)
                data.writeInt(resource.phase.wireCode)
                data.writeNullableLong(resource.rssBytes)
                data.writeInt(resource.openFileDescriptorCount ?: ABSENT_INT)
                data.writeNullableLong(resource.temporaryStorageBytes)
                data.writeNullableLong(resource.outputBytes)
            }
        }
        return output.toByteArray().also { require(it.size in 1..MAX_ENCODED_BYTES) }
    }

    fun decode(bytes: ByteArray): JavaProviderObservation {
        require(bytes.size in 1..MAX_ENCODED_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC)
            val startProfile = enumValue<JavaProviderStartProfile>(data.readInt()) { it.wireCode }
            val durations = listOf(
                JavaProviderObservedPhase.COMPILE to data.readNullableLong(),
                JavaProviderObservedPhase.D8 to data.readNullableLong(),
                JavaProviderObservedPhase.LOAD to data.readNullableLong(),
                JavaProviderObservedPhase.RUN to data.readNullableLong(),
                JavaProviderObservedPhase.TERMINATION to data.readNullableLong(),
            )
            val cacheOutcome = enumValue<JavaProviderCacheOutcome>(data.readInt()) { it.wireCode }
            val missCode = data.readInt()
            val missReason = if (missCode == ABSENT_INT) null else {
                enumValue<JavaProviderCacheMissReason>(missCode) { it.wireCode }
            }
            val count = data.readInt()
            require(count in 0..JavaProviderObservationPolicy.MAX_RESOURCE_SAMPLES)
            val collector = JavaProviderObservationCollector(startProfile)
            durations.forEach { (phase, value) ->
                if (value != null) require(collector.recordDuration(phase, value))
            }
            if (cacheOutcome != JavaProviderCacheOutcome.NOT_EVALUATED) {
                require(collector.recordCache(cacheOutcome, missReason))
            } else {
                require(missReason == null)
            }
            repeat(count) {
                val resource = JavaProviderResourceObservation(
                    process = enumValue<JavaProviderObservedProcess>(data.readInt()) { it.wireCode },
                    phase = enumValue<JavaProviderObservedPhase>(data.readInt()) { it.wireCode },
                    rssBytes = data.readNullableLong(),
                    openFileDescriptorCount = data.readInt().let { if (it == ABSENT_INT) null else it },
                    temporaryStorageBytes = data.readNullableLong(),
                    outputBytes = data.readNullableLong(),
                )
                require(collector.recordResource(resource))
            }
            require(data.read() == -1)
            return collector.snapshot()
        }
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) = writeLong(value ?: ABSENT_LONG)
    private fun DataInputStream.readNullableLong(): Long? = readLong().let { if (it == ABSENT_LONG) null else it }

    private inline fun <reified T : Enum<T>> enumValue(code: Int, wireCode: (T) -> Int): T =
        enumValues<T>().singleOrNull { wireCode(it) == code } ?: throw IllegalArgumentException("Unknown enum code")
}
