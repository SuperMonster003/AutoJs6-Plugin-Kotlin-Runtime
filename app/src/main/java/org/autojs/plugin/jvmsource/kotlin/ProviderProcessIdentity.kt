package org.autojs.plugin.jvmsource.kotlin

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.FileInputStream

internal data class ProviderProcessIdentity(
    val pid: Int,
    val uid: Int,
    val processName: String,
) {
    companion object {
        fun current(context: Context, expectedSuffix: String): ProviderProcessIdentity {
            val expectedName = context.packageName + expectedSuffix
            val actualName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                readProcProcessName()
            }
            check(actualName == expectedName) {
                "Provider component is running in an unexpected process"
            }
            return ProviderProcessIdentity(Process.myPid(), Process.myUid(), actualName)
        }

        private fun readProcProcessName(): String {
            val bytes = ByteArray(512)
            val count = FileInputStream("/proc/self/cmdline").use { it.read(bytes) }
            check(count in 1..bytes.size)
            var end = 0
            while (end < count && bytes[end] != 0.toByte()) end++
            return bytes.copyOfRange(0, end).toString(Charsets.UTF_8)
        }
    }
}
