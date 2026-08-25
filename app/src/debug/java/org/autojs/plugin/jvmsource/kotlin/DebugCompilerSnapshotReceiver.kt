package org.autojs.plugin.jvmsource.kotlin

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import java.io.File

/** Signature-protected, debug-only resource probe for the M7 device stress harness. */
class DebugCompilerSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CAPTURE_COMPILER_SNAPSHOT || !isOrderedBroadcast) return
        Runtime.getRuntime().gc()
        System.runFinalization()
        Runtime.getRuntime().gc()
        val resource = JavaProviderResourceProbe.capture(
            process = JavaProviderObservedProcess.COMPILER,
            phase = JavaProviderObservedPhase.TERMINATION,
            temporaryStorageBytes = null,
            outputBytes = null,
        )
        val workspaceRoot = File(
            AndroidPrivateDirectoryAnchor.cache(context),
            "jvm-source-kotlin-sessions",
        )
        resultCode = Activity.RESULT_OK
        setResultExtras(Bundle().apply {
            putInt(EXTRA_PID, Process.myPid())
            resource.rssBytes?.let { putLong(EXTRA_RSS_BYTES, it) }
            resource.openFileDescriptorCount?.let { putInt(EXTRA_OPEN_FD_COUNT, it) }
            putInt(EXTRA_WORKSPACE_DIRECTORY_COUNT, workspaceRoot.listFiles()?.count(File::isDirectory) ?: 0)
        })
    }

    companion object {
        const val ACTION_CAPTURE_COMPILER_SNAPSHOT =
            "io.github.supermonster003.autojs6.plugin.kotlin.runtime.debug.CAPTURE_COMPILER_SNAPSHOT"
        const val EXTRA_PID = "pid"
        const val EXTRA_RSS_BYTES = "rssBytes"
        const val EXTRA_OPEN_FD_COUNT = "openFileDescriptorCount"
        const val EXTRA_WORKSPACE_DIRECTORY_COUNT = "workspaceDirectoryCount"
    }
}
