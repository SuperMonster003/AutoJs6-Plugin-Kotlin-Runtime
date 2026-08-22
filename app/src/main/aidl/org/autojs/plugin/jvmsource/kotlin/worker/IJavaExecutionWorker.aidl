package org.autojs.plugin.jvmsource.kotlin.worker;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.jvmsource.api.IJvmHostBridge;
import org.autojs.plugin.jvmsource.kotlin.worker.IJavaExecutionCallback;

// Synchronous admission barrier: the worker pins the compiler Binder PID before queuing work.
interface IJavaExecutionWorker {

    void execute(
        in byte[] requestMetadata,
        long generation,
        long compilationElapsedMillis,
        long classArtifactSizeBytes,
        in byte[] classArtifactSha256,
        long dexArtifactSizeBytes,
        in byte[] dexArtifactSha256,
        in String[] expectedClassDescriptors,
        in ParcelFileDescriptor dexFd,
        in ParcelFileDescriptor stdoutFd,
        in ParcelFileDescriptor stderrFd,
        IJvmHostBridge hostBridge,
        IJavaExecutionCallback callback
    );

    void cancel(in byte[] requestId, long generation, int reasonWireCode);

    void terminate(in byte[] requestId, long generation);
}
