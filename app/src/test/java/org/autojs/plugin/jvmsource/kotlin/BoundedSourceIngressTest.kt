package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedSourceIngressTest {
    @Test
    fun acceptsAStreamExactlyAtTheProtocolMaximum() {
        val maximum = JvmSourceContract.MAX_SOURCE_BYTES
        val source = ByteArray(maximum.toInt()) { index -> (index and 0x7f).toByte() }
        var activityChecks = 0

        val accepted = BoundedSourceIngress.read(
            input = ByteArrayInputStream(source),
            declaredSizeBytes = maximum,
            maximumSizeBytes = maximum,
            ensureActive = { activityChecks++ },
        )

        assertEquals(maximum, accepted.size.toLong())
        assertEquals(source.first(), accepted.first())
        assertEquals(source.last(), accepted.last())
        assertTrue(activityChecks > 1)
    }

    @Test
    fun rejectsTheFirstByteBeyondTheProtocolMaximum() {
        val maximum = JvmSourceContract.MAX_SOURCE_BYTES
        val source = ByteArray((maximum + 1L).toInt())

        val failure = assertThrows(JavaProviderFailure::class.java) {
            BoundedSourceIngress.read(
                input = ByteArrayInputStream(source),
                declaredSizeBytes = maximum,
                maximumSizeBytes = maximum,
            )
        }

        assertEquals(JvmSourceErrorCode.SOURCE_TOO_LARGE, failure.code)
        assertEquals(JvmSourceFailurePhase.INPUT, failure.phase)
    }
}
