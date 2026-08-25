package org.autojs.plugin.jvmsource.kotlin.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process

/** Debug-only entry point used to exercise the compiler process's worker-death handling. */
class DebugWorkerKillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KILL_WORKER) return
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val ACTION_KILL_WORKER =
            "io.github.supermonster003.autojs6.plugin.kotlin.runtime.debug.KILL_WORKER"
    }
}
