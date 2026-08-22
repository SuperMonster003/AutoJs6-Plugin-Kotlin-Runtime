package org.autojs.plugin.jvmsource.kotlin.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ReadOnlyDexWritePolicyTest {
    @Test
    fun android14WriteOrderMarksReadOnlyBeforeTheFirstContentByte() {
        val target = RecordingTarget()

        ReadOnlyDexWritePolicy.write(byteArrayOf(1, 2, 3), target)

        assertEquals(
            listOf("create-empty", "open-for-write", "mark-read-only", "first-content-write", "sync", "close"),
            target.events,
        )
    }

    @Test
    fun aReadOnlyFailureClosesTheDescriptorWithoutWritingContent() {
        val target = RecordingTarget(failReadOnly = true)

        assertThrows(IOException::class.java) {
            ReadOnlyDexWritePolicy.write(byteArrayOf(1), target)
        }

        assertEquals(listOf("create-empty", "open-for-write", "mark-read-only", "close"), target.events)
    }

    private class RecordingTarget(
        private val failReadOnly: Boolean = false,
    ) : ReadOnlyDexWritePolicy.Target {
        val events = mutableListOf<String>()

        override fun createEmpty() {
            events += "create-empty"
        }

        override fun openForWrite(): ReadOnlyDexWritePolicy.OpenOutput {
            events += "open-for-write"
            return object : ReadOnlyDexWritePolicy.OpenOutput {
                override fun writeContent(bytes: ByteArray) {
                    events += "first-content-write"
                }

                override fun sync() {
                    events += "sync"
                }

                override fun close() {
                    events += "close"
                }
            }
        }

        override fun markReadOnly() {
            events += "mark-read-only"
            if (failReadOnly) throw IOException("read-only failed")
        }
    }
}
