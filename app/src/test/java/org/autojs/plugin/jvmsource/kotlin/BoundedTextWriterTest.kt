package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedTextWriterTest {
    @Test
    fun countsUtf8BytesAndIncludesMarkerInsideTheBudget() {
        val writer = BoundedTextWriter(64)

        writer.write("你".repeat(100))
        val result = writer.value()

        assertTrue(writer.truncated)
        assertTrue(result.endsWith("\n[diagnostic output truncated]"))
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 64)
        assertFalse(result.any { Character.isSurrogate(it) })
    }

    @Test
    fun preservesASupplementaryCodePointSplitAcrossWrites() {
        val writer = BoundedTextWriter(64)
        val emoji = "\uD83D\uDE00"

        writer.write(charArrayOf(emoji[0]), 0, 1)
        writer.write(charArrayOf(emoji[1]), 0, 1)

        assertEquals(emoji, writer.value())
        assertFalse(writer.truncated)
        assertEquals(4, writer.value().toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun neverSplitsASupplementaryCodePointAtTheLimit() {
        val writer = BoundedTextWriter(32)

        writer.write("a".repeat(31) + "\uD83D\uDE00")
        val result = writer.value()

        assertTrue(writer.truncated)
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 32)
        assertTrue(result.endsWith("[diagnostic output truncated]"))
    }
}
