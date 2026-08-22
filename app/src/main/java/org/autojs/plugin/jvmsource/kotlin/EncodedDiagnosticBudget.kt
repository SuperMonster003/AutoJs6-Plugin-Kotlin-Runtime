package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic

internal object EncodedDiagnosticBudget {
    fun encodeWithin(diagnostic: JvmSourceDiagnostic, remainingBytes: Int): ByteArray? {
        if (remainingBytes <= 0) return null
        val full = JvmSourceCodec.encodeDiagnostic(diagnostic)
        if (full.size <= remainingBytes) return full

        val codePoints = diagnostic.message.codePointCount(0, diagnostic.message.length)
        var lower = 0
        var upper = codePoints
        var accepted: ByteArray? = null
        while (lower <= upper) {
            val middle = (lower + upper) ushr 1
            val prefixEnd = diagnostic.message.offsetByCodePoints(0, middle)
            val candidate = diagnostic.copy(
                message = diagnostic.message.substring(0, prefixEnd) + TRUNCATION_MARKER,
            )
            val encoded = JvmSourceCodec.encodeDiagnostic(candidate)
            if (encoded.size <= remainingBytes) {
                accepted = encoded
                lower = middle + 1
            } else {
                upper = middle - 1
            }
        }
        return accepted
    }

    private const val TRUNCATION_MARKER = "~"
}
