package org.autojs.plugin.jvmsource.kotlin.service

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class SerialCallbackLane : Closeable {
    private val executor: ExecutorService = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(64),
        { runnable -> Thread(runnable, "jvm-source-java-callback").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun dispatch(block: () -> Unit, onFailure: (Throwable) -> Unit) {
        try {
            executor.execute { runCatching(block).onFailure(onFailure) }
        } catch (error: RejectedExecutionException) {
            onFailure(error)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
