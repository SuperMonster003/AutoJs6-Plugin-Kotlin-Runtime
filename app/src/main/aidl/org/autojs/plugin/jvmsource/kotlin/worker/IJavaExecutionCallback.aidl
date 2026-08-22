package org.autojs.plugin.jvmsource.kotlin.worker;

interface IJavaExecutionCallback {

    // Synchronous barrier: worker code cannot run before compiler pins the real Binder caller.
    void onReady(int pid, long generation);

    // Synchronous terminal barriers: the worker retires only after the compiler records terminal.
    void onCompleted(long generation, in byte[] result);

    void onFailed(long generation, in byte[] error);

    void onCancelled(long generation, in byte[] cancellation);

    // Provider-internal, bounded numeric/enum telemetry. Not part of external Protocol V1.
    void onObservation(long generation, in byte[] observation);

    // A validated requested-entry/inner-class stack frame only; no text, class, path, UID or PID.
    void onRuntimeDiagnostic(long generation, int line);
}
