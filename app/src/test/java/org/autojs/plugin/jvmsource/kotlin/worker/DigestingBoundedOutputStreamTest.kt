package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class DigestingBoundedOutputStreamTest {
    @Test
    fun reportsExactlyTheBytesWritten() {
        val delegate = ByteArrayOutputStream()
        val output = DigestingBoundedOutputStream(delegate, 3L)

        output.write("abc".toByteArray())
        output.close()
        val snapshot = output.snapshot()

        assertArrayEquals("abc".toByteArray(), delegate.toByteArray())
        assertEquals(3L, snapshot.sizeBytes)
        assertEquals(JvmSha256.digest("abc".toByteArray()), snapshot.sha256)
    }

    @Test
    fun rejectsAnEntireWriteThatWouldCrossTheLimit() {
        val delegate = ByteArrayOutputStream()
        val output = DigestingBoundedOutputStream(delegate, 3L)

        assertThrows(IOException::class.java) { output.write("abcd".toByteArray()) }
        assertTrue(output.overflowed)
        output.close()
        assertEquals(0L, output.snapshot().sizeBytes)
        assertArrayEquals(ByteArray(0), delegate.toByteArray())
    }
}
