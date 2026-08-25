# Compiler lifecycle and memory isolation

Status: Accepted for M7 on 2026-08-25

## Context

Keeping the compiler process bound makes authenticated artifact-cache hits fast: the designated
device measured a 577 ms cache-cold median and a 46 ms cache-hit median. A fresh disposable worker
is still mandatory for every execution.

M7 also found that an Android cached `:compiler` process is not a safe lifetime boundary for the
embedded Kotlin CLI. In a deliberately harsh debug configuration, compilation caching is disabled,
so every request invokes kotlinc. One 50-session batch stayed in the low hundreds of MiB, but reusing
the same Android cached process for a second batch grew `Dalvik Other` nonlinearly: midpoint PSS was
706,523,136 bytes and final PSS was 1,893,345,280 bytes. File descriptors stayed at 80 and private
workspaces stayed at zero, isolating the issue from descriptor or workspace cleanup.

Calling `KotlinCoreEnvironment.disposeApplicationEnvironment()` after every kotlinc invocation
reduced the first-epoch growth but did not make indefinite ART process reuse safe. Treating that call
alone as the fix was therefore rejected.

## Decision

Use a bounded binding epoch:

1. Keep `JavaSourceCompilerService` alive while the host holds its active provider binding. This
   preserves Protocol continuity and enables cache hits within that epoch.
2. After every real kotlinc invocation, successful or failed, explicitly dispose the Kotlin core
   application/VFS environment. A disposal failure becomes a stable, non-sensitive compilation
   failure rather than being ignored.
3. Run `JavaPluginInfoService` in a separate `:discovery` process. The `:compiler` process is then
   dedicated to the source provider and can be retired without interrupting metadata discovery.
4. When the final source-provider binding is destroyed with no critical session, kill the dedicated
   compiler process immediately after service cleanup. If a compiler/session is still critical,
   retain the existing two-second independent watchdog and then kill the process.
5. Continue to use one new `:worker` process for every execution and hard-retire it after terminal
   delivery or cancellation grace.

## Operational budgets and evidence

The debug M7 resource audit performs 50 mixed sessions after full-path warmup, with caching disabled.
It asserts:

- one compiler PID throughout the active binding;
- 40 distinct successful worker PIDs and strictly increasing generations;
- zero private session directories before, halfway through, and after the batch;
- no more than four additional descriptors (observed `79 → 78`);
- compiler PSS growth no greater than 64 MiB in one test epoch;
- final compiler PSS no greater than 256 MiB;
- no compiler or worker PID after the test client unbinds.

The final `0.4.0-m7` run observed `131,467,264 → 135,791,616 → 164,301,824` PSS bytes, a
32,834,560-byte total increase and 156.7 MiB final PSS. The compiler and worker were absent
immediately after unbind. Two earlier post-fix 50-session epochs used different compiler PIDs
(`9036`, then `10659`) and both retired after unbind, directly covering the condition that previously
caused GiB-scale accumulation.

## Consequences

- The first cache miss in a new binding epoch may pay compiler startup cost. Within an epoch, cache
  hits retain the measured benefit.
- The cache HMAC key remains a non-dumpable process-epoch secret. Entries from an old epoch cannot be
  trusted by a new key and safely degrade to cold compilation/invalidation.
- Process death remains an expected isolation boundary; the host must continue treating provider
  Binder death as reconnectable, as it already must for watchdog and OS process death.
- Metadata discovery no longer shares the compiler process, reducing coupling and making retirement
  explicit.

Indefinite compiler-process keepalive and per-request compiler-process startup were both rejected:
the former is not memory-safe on ART, while the latter would discard cache/startup benefits and add
substantial process churn. A binding-scoped epoch is the smallest boundary supported by the frozen
Protocol 1.1 service shape.
