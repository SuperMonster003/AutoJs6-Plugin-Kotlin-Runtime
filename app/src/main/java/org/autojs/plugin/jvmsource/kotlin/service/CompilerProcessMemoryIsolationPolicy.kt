package org.autojs.plugin.jvmsource.kotlin.service

internal object CompilerProcessMemoryIsolationPolicy {
    fun requireDisabled(setResult: Int, observedDumpable: Int) {
        require(setResult == 0 && observedDumpable == 0) {
            "Compiler process must be non-dumpable before initializing cache authentication"
        }
    }
}
