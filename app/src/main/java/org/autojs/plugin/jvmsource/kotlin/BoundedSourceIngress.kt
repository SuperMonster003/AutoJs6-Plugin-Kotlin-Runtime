package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads one source stream without ever admitting a byte beyond either trusted size bound. */
internal object BoundedSourceIngress {
    fun read(
        input: InputStream,
        declaredSizeBytes: Long,
        maximumSizeBytes: Long,
        ensureActive: () -> Unit = {},
    ): ByteArray {
        val initialCapacity = minOf(declaredSizeBytes, maximumSizeBytes, DEFAULT_BUFFER_SIZE.toLong())
            .coerceAtLeast(0L)
            .toInt()
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total = try {
                Math.addExact(total, count.toLong())
            } catch (error: ArithmeticException) {
                throw tooLarge(error)
            }
            if (total > declaredSizeBytes || total > maximumSizeBytes) {
                throw tooLarge()
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun tooLarge(cause: Throwable? = null) = JavaProviderFailure(
        JvmSourceErrorCode.SOURCE_TOO_LARGE,
        JvmSourceFailurePhase.INPUT,
        "Source stream exceeds its declared or negotiated size limit",
        cause,
    )
}
