package org.autojs.plugin.jvmsource.kotlin

internal enum class CompilationCacheEnablement {
    ENABLED,
    DISABLED,
}

/** Cache authentication is admitted only for a release-like, non-dumpable compiler process. */
internal object CompilationCacheEnablementPolicy {
    fun evaluate(providerDebuggable: Boolean, compilerNonDumpable: Boolean): CompilationCacheEnablement =
        if (!providerDebuggable && compilerNonDumpable) {
            CompilationCacheEnablement.ENABLED
        } else {
            CompilationCacheEnablement.DISABLED
        }
}
