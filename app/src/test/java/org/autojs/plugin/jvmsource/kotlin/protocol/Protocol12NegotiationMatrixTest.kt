package org.autojs.plugin.jvmsource.kotlin.protocol

import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmProviderInfo
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceContractException
import org.autojs.plugin.jvmsource.api.JvmSourceContractViolation
import org.autojs.plugin.jvmsource.api.JvmSourceNegotiation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Protocol12NegotiationMatrixTest {
    @Test
    fun protocol11And12RangesNegotiateTheHighestCommonMinor() {
        val rows = listOf(
            MatrixRow("old host + old plugin", V11, V11, V11, V11, V11),
            MatrixRow("old host + compatible new plugin", V11, V11, V11, V12, V11),
            MatrixRow("old host + 1.2-only plugin", V11, V11, V12, V12, null),
            MatrixRow("compatible new host + old plugin", V11, V12, V11, V11, V11),
            MatrixRow("compatible new host + compatible new plugin", V11, V12, V11, V12, V12),
            MatrixRow("1.2-only host + old plugin", V12, V12, V11, V11, null),
            MatrixRow("1.2-only host + 1.2-only plugin", V12, V12, V12, V12, V12),
        )

        rows.forEach { row ->
            assertEquals(
                row.name,
                row.expected,
                JvmSourceNegotiation.negotiate(
                    providerInfo = provider(row.providerMin, row.providerMax),
                    hostVersionCode = HOST_VERSION_CODE,
                    hostMin = row.hostMin,
                    hostMax = row.hostMax,
                ),
            )
        }
    }

    @Test
    fun incompatible12OnlyProviderFailsWithStableProtocolViolationOnOldHost() {
        val failure = assertThrows(JvmSourceContractException::class.java) {
            JvmSourceNegotiation.requireNegotiated(
                providerInfo = provider(V12, V12),
                hostVersionCode = HOST_VERSION_CODE,
                hostMin = V11,
                hostMax = V11,
            )
        }

        assertEquals(JvmSourceContractViolation.PROTOCOL_INCOMPATIBLE, failure.violation)
    }

    @Test
    fun frozen11CodecRoundTripsAForwardMinorRangeWithoutChangingCurrentConstants() {
        val forwardRange = provider(V11, V12)

        assertEquals(forwardRange, JvmSourceCodec.decodeProviderInfo(JvmSourceCodec.encodeProviderInfo(forwardRange)))
        assertEquals(V11, JvmSourceNegotiation.HOST_MIN)
        assertEquals(V11, JvmSourceNegotiation.HOST_MAX)
        assertEquals(1, JvmSourceContract.PROTOCOL_MAJOR)
        assertEquals(1, JvmSourceContract.PROTOCOL_MINOR)
        assertNull(
            JvmSourceNegotiation.negotiate(
                providerInfo = provider(V12, V12),
                hostVersionCode = HOST_VERSION_CODE,
            ),
        )
    }

    private fun provider(min: JvmProtocolVersion, max: JvmProtocolVersion) = JvmProviderInfo(
        protocolMin = min,
        protocolMax = max,
        providerId = "kotlin-jvm-m10-fixture",
        providerVersionName = "0.7.0-m10",
        providerVersionCode = 7L,
        entryApiVersion = JvmSourceContract.ENTRY_API_VERSION,
        minHostVersionCode = HOST_VERSION_CODE,
    )

    private data class MatrixRow(
        val name: String,
        val hostMin: JvmProtocolVersion,
        val hostMax: JvmProtocolVersion,
        val providerMin: JvmProtocolVersion,
        val providerMax: JvmProtocolVersion,
        val expected: JvmProtocolVersion?,
    )

    private companion object {
        const val HOST_VERSION_CODE = 5276L
        val V11 = JvmProtocolVersion(1, 1)
        val V12 = JvmProtocolVersion(1, 2)
    }
}

