package org.autojs.plugin.jvmsource.kotlin.service

import android.system.Os
import android.system.OsConstants

/** Fail-closed process-epoch secret boundary for the compiler-only cache HMAC key. */
internal object CompilerProcessMemoryIsolation {
    @Volatile
    private var nonDumpableEnforced = false

    fun enforceNonDumpable() {
        val setResult = Os.prctl(OsConstants.PR_SET_DUMPABLE, 0L, 0L, 0L, 0L)
        val observedDumpable = Os.prctl(OsConstants.PR_GET_DUMPABLE, 0L, 0L, 0L, 0L)
        CompilerProcessMemoryIsolationPolicy.requireDisabled(setResult, observedDumpable)
        nonDumpableEnforced = true
    }

    fun wasNonDumpableEnforced(): Boolean = nonDumpableEnforced
}
