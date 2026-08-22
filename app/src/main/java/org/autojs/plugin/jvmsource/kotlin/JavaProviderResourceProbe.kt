package org.autojs.plugin.jvmsource.kotlin

import android.os.Debug
import java.io.File

/** Best-effort local probes; unavailable or out-of-policy values are omitted by the collector. */
internal object JavaProviderResourceProbe {
    fun capture(
        process: JavaProviderObservedProcess,
        phase: JavaProviderObservedPhase,
        temporaryStorageBytes: Long?,
        outputBytes: Long?,
    ): JavaProviderResourceObservation = JavaProviderResourceObservation(
        process = process,
        phase = phase,
        rssBytes = runCatching { Math.multiplyExact(Debug.getPss().toLong(), 1024L) }.getOrNull(),
        openFileDescriptorCount = runCatching { File("/proc/self/fd").list()?.size }.getOrNull(),
        temporaryStorageBytes = temporaryStorageBytes,
        outputBytes = outputBytes,
    )
}
