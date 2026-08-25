# M7 compile-latency and cache benchmark

Date: 2026-08-25 (Asia/Shanghai)

## Method

The test-only `m7-harness` module runs inside the real AutoJs6 instrumentation target process. Its
Binder calls therefore carry the production host UID and exercise all provider caller, signature,
component, Protocol 1.1, artifact, and worker checks. It does not bypass production admission.

`benchmarkFiveCacheColdAndWarmPairs` creates five distinct valid source units. Each source is run
twice under one non-debuggable provider binding: the first request is a cache miss and the second is
an authenticated cache hit. Values come directly from `JvmSourceResult.compilationElapsedMillis`
and `executionElapsedMillis`; each list uses its middle sorted value as the median.

Device and software:

| Field | Value |
|---|---|
| ADB serial | `QV710AF65F` |
| Device | Sony `XQ-AT72`, Android 12 / API 31, arm64-v8a |
| AutoJs6 | `6.8.0`, version code 5276 |
| Provider | `0.4.0-m7`, version code 4, release/non-debuggable |
| Run ID | `m7-benchmark-20260825-145841` |

## Observed values

| Metric | Five observations (ms) | Median |
|---|---|---:|
| cache-cold compilation | 1,117, 599, 577, 567, 530 | **577 ms** |
| cache-hit compilation/materialization | 47, 42, 43, 46, 58 | **46 ms** |
| cache-cold execution | 28, 30, 31, 30, 33 | **30 ms** |
| cache-hit execution | 29, 20, 36, 31, 30 | **30 ms** |

The cache-hit compilation median is about **12.5× faster** than cache-cold compilation. Execution
medians are identical, which isolates the difference to compilation/cache materialization rather
than disposable-worker execution. All ten requests used distinct worker PIDs.

The expected provider-internal telemetry delta is exactly:

| Counter | Delta |
|---|---:|
| hits | 5 |
| misses | 5 |
| publications | 5 |
| publication failures | 0 |

Protocol 1.1 intentionally exports no cache counters. `CompilationCacheTelemetryTest` therefore
replays the observed five-pair and 50-session event sequences against the same saturating counters,
including release and cache-disabled debug policies. This keeps protocol output stable while making
the documented deltas executable assertions.

## Reproduction

The complete one-click runner builds offline, installs the host-aligned APKs, writes raw
instrumentation/logcat evidence under `build/m7-evidence/`, and restores the release APK:

```powershell
.\scripts\stress\run-m7-device.ps1 -Serial QV710AF65F
```

On Bash-capable hosts:

```bash
scripts/stress/run-m7-device.sh QV710AF65F
```

Five pairs on one phone are a regression baseline, not a cross-device performance guarantee.
Thermal state, Android process state, and storage cache can change absolute time. The paired design,
medians, identical source within each pair, and execution control values reduce that noise.

The compiler keepalive and memory-reclamation decision informed by this benchmark is recorded in
[`../decisions/compiler-lifecycle.md`](../decisions/compiler-lifecycle.md).
