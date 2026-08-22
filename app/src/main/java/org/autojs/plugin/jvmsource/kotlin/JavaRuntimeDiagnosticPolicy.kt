package org.autojs.plugin.jvmsource.kotlin

import java.util.Collections
import java.util.IdentityHashMap

/** Extracts only a bounded requested-source line from a runtime failure; no Throwable text is retained. */
internal object JavaRuntimeDiagnosticPolicy {
    fun sourceLine(
        error: Throwable,
        entryClassName: String = "Main",
        sourceFileName: String = "Main.kt",
    ): Int? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        repeat(MAX_CAUSE_DEPTH) {
            val value = current ?: return null
            if (!seen.add(value)) return null
            value.stackTrace.asSequence().take(MAX_STACK_FRAMES).firstOrNull { frame ->
                (frame.className == entryClassName || frame.className.startsWith("$entryClassName\$")) &&
                    frame.fileName == sourceFileName && frame.lineNumber in 1..MAX_SOURCE_POSITION
            }?.let { return it.lineNumber }
            current = value.cause
        }
        return null
    }

    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_STACK_FRAMES = 256
    private const val MAX_SOURCE_POSITION = 1_000_000
}
