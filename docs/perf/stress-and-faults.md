# M7 device stress and fault-injection record

Date: 2026-08-25 (Asia/Shanghai)

All tests used Sony `XQ-AT72` (`QV710AF65F`), Android 12 / API 31, AutoJs6 6.8.0 build 5276, and
the production Protocol 1.1 Binder path. Instrumentation ran inside the host process with its real
UID and host-aligned signer.

## Release 50-session mix

Canonical run: `m7-stress-release-20260825-145841`, provider `0.4.0-m7` build 4,
release/non-debuggable. Result: `OK (1 test)` in 46.121 seconds.

| Item | Observed |
|---|---|
| total sessions | 50 |
| successful compile + execution | 40 |
| expected compilation failures | 5 |
| requested execution cancellations | 5 |
| compiler PID | `13087`, stable for the binding |
| successful worker PIDs | 40 values, all distinct |
| worker generations | 5–53 with cancellation/failure gaps; strictly increasing |
| expected cache telemetry delta | 44 hits, 6 misses, 1 publication, 0 publication failures |
| process state after unbind | compiler and worker absent |

The mix is interleaved in ten cycles: four successful sessions followed by a compilation failure on
even cycles or a mid-execution cancellation on odd cycles. This prevents one path from receiving an
artificially isolated phase of the test.

## Debug resource audit

Canonical run: `m7-stress-resource-final-20260825b`, provider `0.4.0-m7` build 4,
debuggable/cache-disabled. Result: `OK (1 test)` in 112.696 seconds. Because debug policy disables
the authenticated cache, all 50 measured requests invoke the compiler and the expected telemetry is
0 hits, 50 `CACHE_DISABLED` misses, and 0 publications.

| Snapshot | Compiler PID | PSS bytes | Open FDs | Session directories |
|---|---:|---:|---:|---:|
| after full-path warmup | 17251 | 131,467,264 | 79 | 0 |
| after 25 mixed sessions | 17251 | 135,791,616 | 78 | 0 |
| after 50 mixed sessions | 17251 | 164,301,824 | 78 | 0 |

Total PSS growth was 32,834,560 bytes (31.31 MiB), final PSS was 156.69 MiB, and FD delta was -1.
All 40 successful worker PIDs were distinct, generations were strictly increasing, and compiler and
worker PIDs were empty after the binding closed. See the compiler lifecycle decision for the
regression that led to explicit compiler-process epoch retirement.

## Fault matrix

| Scenario | Variant | Contract result | Run ID |
|---|---|---|---|
| source stream exceeds `MAX_SOURCE_BYTES` by one byte | release | `SOURCE_TOO_LARGE / INPUT` | `m7-fault-oversize-20260825-145841` |
| cache-cold compiler work exceeds the 200 ms request deadline but completes inside the hard-kill grace | release | `TIMEOUT / COMPILATION` | `m7-fault-compile-timeout-20260825-145841` |
| cached worker enters an infinite loop | release | `TIMEOUT / EXECUTION`, worker hard-retired | `m7-fault-execution-timeout-20260825-145841` |
| worker process receives an out-of-band debug kill signal | debug | `WORKER_DIED / EXECUTION` | `m7-fault-external-kill-20260825-145841` |

The external-kill fixture is release-safe by construction. Android's non-dumpable worker cannot be
seen or signalled through `run-as`, which the initial probe confirmed. The repository therefore adds
a debug-source-set receiver in the `:worker` process, exported only under the existing signature
permission. The same-signed host harness sends an explicit broadcast; the worker calls
`Process.killProcess` on itself and the unmodified compiler-side Binder death path reports
`WORKER_DIED`. Neither the receiver nor the resource snapshot probe exists in the release APK.

A separate heavier compiler-starvation probe showed the containment boundary for a genuinely
non-cooperative compiler invocation: after the two-second cancellation grace the entire compiler
process is killed and Android restarts the bound service. Since the process carrying the callback is
gone, that extreme path cannot deliver a Protocol terminal from the old Binder; host-side Binder
death/reconnect is the expected signal. The canonical 20-function fixture covers the contract-level
`TIMEOUT / COMPILATION` terminal, while the heavy probe covers hard containment.

## Reproduction

Windows PowerShell:

```powershell
.\scripts\stress\run-m7-device.ps1 -Serial QV710AF65F
```

Bash:

```bash
scripts/stress/run-m7-device.sh QV710AF65F
```

Both runners build with `--offline`, install the test-only Harness, run release benchmark/stress and
three release fault cases, temporarily install debug for the resource audit and external-kill case,
strictly reject instrumentation crashes/failure text, verify compiler/worker absence after every
unbind, save raw output under `build/m7-evidence/`, and restore the release APK in cleanup.

PowerShell options `-SkipBuild` and `-SkipResourceAudit` support a faster already-built regression
pass. Bash uses `M7_SKIP_BUILD=1` and `M7_SKIP_RESOURCE_AUDIT=1` for the same purpose.
