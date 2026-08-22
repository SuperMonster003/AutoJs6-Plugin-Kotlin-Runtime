package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import java.lang.reflect.Array
import java.math.BigDecimal
import java.math.BigInteger
import java.util.IdentityHashMap

internal object WorkerJsonValue {
    fun encode(value: Any?): String = try {
        BoundedJsonBuilder(JvmSourceContract.MAX_RESULT_JSON_BYTES).apply {
            appendValue(value, 0, IdentityHashMap())
        }.toString()
    } catch (error: JavaProviderFailure) {
        throw error
    } catch (error: Throwable) {
        throw JavaProviderFailure(
            JvmSourceErrorCode.EXECUTION_FAILED,
            JvmSourceFailurePhase.EXECUTION,
            "AutoJsJvmEntry returned a value outside the R1 JSON profile",
            error,
        )
    }

    private class BoundedJsonBuilder(private val maximumBytes: Int) {
        private val output = StringBuilder()
        private var byteCount = 0

        fun appendValue(value: Any?, depth: Int, active: IdentityHashMap<Any, Boolean>) {
            when (value) {
                null -> appendAscii("null")
                is Boolean -> appendAscii(value.toString())
                is Byte, is Short, is Int, is Long, is BigInteger -> appendNumber(value.toString())
                is Float -> {
                    require(value.isFinite()) { "Non-finite JSON number" }
                    appendNumber(value.toString())
                }
                is Double -> {
                    require(value.isFinite()) { "Non-finite JSON number" }
                    appendNumber(value.toString())
                }
                is BigDecimal -> appendNumber(value.toString())
                is CharSequence -> appendString(value.toString())
                is Char -> appendString(value.toString())
                is Map<*, *> -> appendContainer(value, depth, active) {
                    appendAscii("{")
                    var first = true
                    value.forEach { (key, item) ->
                        require(key is String) { "JSON object keys must be strings" }
                        if (!first) appendAscii(",")
                        first = false
                        appendString(key)
                        appendAscii(":")
                        appendValue(item, depth + 1, active)
                    }
                    appendAscii("}")
                }
                is Iterable<*> -> appendContainer(value, depth, active) {
                    appendAscii("[")
                    var first = true
                    value.forEach { item ->
                        if (!first) appendAscii(",")
                        first = false
                        appendValue(item, depth + 1, active)
                    }
                    appendAscii("]")
                }
                else -> if (value.javaClass.isArray) {
                    appendContainer(value, depth, active) {
                        appendAscii("[")
                        repeat(Array.getLength(value)) { index ->
                            if (index > 0) appendAscii(",")
                            appendValue(Array.get(value, index), depth + 1, active)
                        }
                        appendAscii("]")
                    }
                } else {
                    throw IllegalArgumentException("Unsupported JSON result type")
                }
            }
        }

        private inline fun appendContainer(
            value: Any,
            depth: Int,
            active: IdentityHashMap<Any, Boolean>,
            block: () -> Unit,
        ) {
            require(depth < JvmSourceContract.MAX_JSON_DEPTH) { "JSON result is nested too deeply" }
            require(active.put(value, true) == null) { "JSON result contains a reference cycle" }
            try {
                block()
            } finally {
                active.remove(value)
            }
        }

        private fun appendNumber(value: String) {
            require(value.length <= 256 && JSON_NUMBER.matches(value)) { "Invalid JSON number" }
            appendAscii(value)
        }

        private fun appendString(value: String) {
            appendAscii("\"")
            var index = 0
            while (index < value.length) {
                val char = value[index]
                when (char) {
                    '"' -> appendAscii("\\\"")
                    '\\' -> appendAscii("\\\\")
                    '\b' -> appendAscii("\\b")
                    '\u000C' -> appendAscii("\\f")
                    '\n' -> appendAscii("\\n")
                    '\r' -> appendAscii("\\r")
                    '\t' -> appendAscii("\\t")
                    else -> when {
                        char.code < 0x20 -> appendAscii("\\u%04x".format(char.code))
                        Character.isHighSurrogate(char) && index + 1 < value.length &&
                            Character.isLowSurrogate(value[index + 1]) -> {
                            appendCodePoint(Character.toCodePoint(char, value[index + 1]))
                            index++
                        }
                        Character.isSurrogate(char) -> appendCodePoint(0xfffd)
                        else -> appendCodePoint(char.code)
                    }
                }
                index++
            }
            appendAscii("\"")
        }

        private fun appendAscii(value: String) {
            require(value.all { it.code <= 0x7f })
            ensureCapacity(value.length)
            output.append(value)
            byteCount += value.length
        }

        private fun appendCodePoint(codePoint: Int) {
            val bytes = when {
                codePoint <= 0x7f -> 1
                codePoint <= 0x7ff -> 2
                codePoint <= 0xffff -> 3
                else -> 4
            }
            ensureCapacity(bytes)
            output.appendCodePoint(codePoint)
            byteCount += bytes
        }

        private fun ensureCapacity(additionalBytes: Int) {
            require(byteCount <= maximumBytes - additionalBytes) { "JSON result exceeds its byte limit" }
        }

        override fun toString(): String = output.toString()

        private companion object {
            val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
        }
    }
}
