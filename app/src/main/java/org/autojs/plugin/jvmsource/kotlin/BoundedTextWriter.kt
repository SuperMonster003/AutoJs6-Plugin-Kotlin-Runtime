package org.autojs.plugin.jvmsource.kotlin

import java.io.Writer

internal class BoundedTextWriter(private val maximumBytes: Int) : Writer() {
    private val content = StringBuilder(maximumBytes.coerceAtMost(4_096))
    private var contentBytes = 0
    private var pendingHighSurrogate: Char? = null
    var truncated: Boolean = false
        private set

    init { require(maximumBytes > 0) { "UTF-8 byte limit must be positive" } }

    override fun write(buffer: CharArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > buffer.size - length) {
            throw IndexOutOfBoundsException("Invalid writer range")
        }
        if (length <= 0) return
        if (truncated) return
        var index = offset
        val end = offset + length

        pendingHighSurrogate?.let { pending ->
            pendingHighSurrogate = null
            if (index < end && Character.isLowSurrogate(buffer[index])) {
                appendCodePoint(Character.toCodePoint(pending, buffer[index]))
                index++
            } else {
                appendCodePoint(REPLACEMENT_CODE_POINT)
            }
        }

        while (index < end && !truncated) {
            val char = buffer[index]
            when {
                Character.isHighSurrogate(char) && index + 1 < end &&
                    Character.isLowSurrogate(buffer[index + 1]) -> {
                    appendCodePoint(Character.toCodePoint(char, buffer[index + 1]))
                    index += 2
                }
                Character.isHighSurrogate(char) -> {
                    pendingHighSurrogate = char
                    index++
                }
                Character.isLowSurrogate(char) -> {
                    appendCodePoint(REPLACEMENT_CODE_POINT)
                    index++
                }
                else -> {
                    appendCodePoint(char.code)
                    index++
                }
            }
        }
        if (truncated) pendingHighSurrogate = null
    }

    override fun flush() = Unit

    override fun close() = Unit

    fun value(): String {
        pendingHighSurrogate?.let {
            pendingHighSurrogate = null
            appendCodePoint(REPLACEMENT_CODE_POINT)
        }
        if (!truncated) return content.toString()
        val marker = if (maximumBytes >= TRUNCATION_MARKER_BYTES) TRUNCATION_MARKER else "~"
        val markerBytes = marker.toByteArray(Charsets.UTF_8).size
        while (contentBytes + markerBytes > maximumBytes && content.isNotEmpty()) {
            val codePoint = Character.codePointBefore(content, content.length)
            content.setLength(content.length - Character.charCount(codePoint))
            contentBytes -= utf8Size(codePoint)
        }
        return content.toString() + marker
    }

    private fun appendCodePoint(codePoint: Int) {
        val encodedBytes = utf8Size(codePoint)
        if (contentBytes > maximumBytes - encodedBytes) {
            truncated = true
            return
        }
        content.appendCodePoint(codePoint)
        contentBytes += encodedBytes
    }

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint <= 0x7f -> 1
        codePoint <= 0x7ff -> 2
        codePoint <= 0xffff -> 3
        else -> 4
    }

    private companion object {
        const val REPLACEMENT_CODE_POINT = 0xfffd
        const val TRUNCATION_MARKER = "\n[diagnostic output truncated]"
        val TRUNCATION_MARKER_BYTES = TRUNCATION_MARKER.toByteArray(Charsets.UTF_8).size
    }
}
