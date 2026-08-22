package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import java.io.File

internal data class SanitizedKotlinDiagnostic(
    val severity: JvmDiagnosticSeverity,
    val code: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)

internal object KotlinDiagnosticSanitizer {
    private val ABSOLUTE_UNIX_PATH = Regex("(?:/[\\w.@$+~%-]+){2,}/?")
    private val ABSOLUTE_WINDOWS_PATH = Regex("[A-Za-z]:[\\\\/][^\\s\"'<>|]*")
    private val DIGEST_LIKE = Regex("\\b[0-9a-fA-F]{32,}\\b")
    private val PROCESS_IDENTITY = Regex("(?i)\\b(?:uid|pid)\\s*[=:]\\s*\\d+")
    private val BINDER_REFERENCE = Regex("(?i)\\bBinder@[0-9a-fA-F]+")
    private val SENSITIVE_METADATA = Regex("(?i)\\b(?:component|signer|classpath|uid|pid)\\s*[=:]")
    private const val REDACTED = "<redacted>"
    private const val MAX_SOURCE_POSITION = 1_000_000

    fun sanitize(
        value: RawKotlinDiagnostic,
        byteLimit: Int,
        privateFiles: Collection<File>,
        sourceFile: File,
    ): SanitizedKotlinDiagnostic {
        var text = value.message.trim()
        privateFiles
            .flatMap(::pathVariants)
            .distinct()
            .sortedByDescending(String::length)
            .forEach { path -> text = text.replace(path, REDACTED) }
        text = ABSOLUTE_WINDOWS_PATH.replace(text, REDACTED)
        text = ABSOLUTE_UNIX_PATH.replace(text, REDACTED)
        text = DIGEST_LIKE.replace(text, REDACTED)
        text = PROCESS_IDENTITY.replace(text, REDACTED)
        text = BINDER_REFERENCE.replace(text, REDACTED)
        val message = if (
            text.isBlank() ||
            REDACTED in text ||
            SENSITIVE_METADATA.containsMatchIn(text)
        ) {
            fallback(value.severity)
        } else {
            val bounded = BoundedTextWriter(byteLimit).also { it.write(text) }.value()
            limitCodePoints(bounded, JvmSourceContract.MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS)
                .ifBlank { fallback(value.severity) }
        }
        val locatedInSource = value.sourcePath?.let { reported ->
            pathVariants(sourceFile).any { it == reported }
        } == true
        val line = value.line?.takeIf { locatedInSource && it in 1..MAX_SOURCE_POSITION }
        val column = value.column?.takeIf { line != null && it in 1..MAX_SOURCE_POSITION }
        return SanitizedKotlinDiagnostic(
            severity = value.severity,
            code = when (value.severity) {
                JvmDiagnosticSeverity.ERROR -> "KOTLIN_ERROR"
                JvmDiagnosticSeverity.WARNING -> "KOTLIN_WARNING"
                JvmDiagnosticSeverity.INFO -> "KOTLIN_INFO"
            },
            message = message,
            line = line?.takeIf { column != null },
            column = column?.takeIf { line != null },
        )
    }

    private fun pathVariants(file: File): Set<String> = buildSet {
        add(file.path)
        add(file.absolutePath)
        runCatching { file.canonicalPath }.getOrNull()?.let(::add)
    }.filter { it.length > 1 }.toSet()

    private fun limitCodePoints(value: String, maximum: Int): String {
        val count = value.codePointCount(0, value.length)
        if (count <= maximum) return value
        return value.substring(0, value.offsetByCodePoints(0, maximum - 1)) + "~"
    }

    private fun fallback(severity: JvmDiagnosticSeverity): String = when (severity) {
        JvmDiagnosticSeverity.ERROR -> "Kotlin compilation error"
        JvmDiagnosticSeverity.WARNING -> "Kotlin compiler warning"
        JvmDiagnosticSeverity.INFO -> "Kotlin compiler diagnostic"
    }
}
