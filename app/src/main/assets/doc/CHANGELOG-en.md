******

### Release History

******

# v0.7.2

###### 2026/09/19

* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3
* `Improvement` Raise compileSdk and targetSdk to 37 (Android 17); the plugin's behavior does not depend on the new target

# v0.7.1

###### 2026/09/13

* `Fix` The plugin center can activate a newly installed provider through a protected entry; displayed metadata follows the installed package
* `Improvement` Host activation, plugin metadata, localized documentation and signed release collection follow the common plugin conventions

# v0.7.0

###### 2026/09/11

* `Improvement` Build verification rejects accidental native dependencies and produces a JSON report

# v0.7.0-m10

###### 2026/08/26

* `Hint` Released capabilities stay at Protocol 1.1 / Entry API 2; no 1.2 capability opens before the host lands it
* `Feature` Added the JVM Source Protocol 1.2 capability proposal and submitted it for host review: bounded clipboard, user-granted documents, host-proxied HTTPS, and host-owned notifications
* `Feature` Added a four-stage host-call pipeline (authorization → payload validation → dispatch → response validation); existing `app.launch` and `toast.show` migrated with unchanged behavior
* `Improvement` Upgraded the frozen protocol AARs to schema-2 provenance locking, with a staging-only refresh script and a complete refresh SOP
* `Improvement` Added seven 1.1/1.2 protocol negotiation combination tests covering old/new host-plugin pairings, downgrade, and stable rejection paths

# v0.6.0-m9

###### 2026/08/26

* `Hint` `Dispatchers.Main`, full `kotlin-reflect`, and kotlinx-serialization remain outside the script surface
* `Feature` Added the exactly pinned `kotlinx-coroutines-core-jvm` 1.11.0 to the script libraries, enabling structured concurrency and cooperative cancellation
* `Feature` Added the ready-to-run coroutine example `samples/coroutines.kt`
* `Improvement` Runtime fingerprints and compilation cache keys now include the coroutine library identity, so all pre-upgrade caches invalidate automatically

# v0.5.0-m8

###### 2026/08/25

* `Feature` Added bilingual walkthrough samples for four common compilation errors: missing import, type mismatch, missing entry interface, and package/request mismatch
* `Fix` Fixed K2 source line/column positions being dropped due to Windows path separator differences
* `Fix` A leading UTF-8 BOM is now stripped without shifting first-line positions
* `Improvement` Clearer package policy wording: only ordinary ASCII identifiers are supported, with stable readable messages instead of misleading errors on rejection
* `Improvement` Diagnostics are budgeted in UTF-8 and truncated only at Unicode code-point boundaries — never half an emoji

# v0.4.0-m7

###### 2026/08/25

* `Feature` Added an on-device stress and fault-injection harness (`m7-harness`) with one-command PowerShell/POSIX device scripts
* `Fix` Fixed compiler state lingering across host binding epochs when the cache is disabled
* `Improvement` Reduced the release APK from 45.34 MB to 30.38 MB (-32.99%) by switching to a deterministic class-only API 24 compiler classpath
* `Improvement` Compilation cache warm hits at a 46 ms median vs 577 ms cold — about 12.5x faster in device benchmarks
* `Improvement` The compiler process now lives per binding and retires on final unbind; metadata discovery moved to the separate `:discovery` process

# v0.3.0-m6

###### 2026/08/25

* `Feature` Added the CHANGELOG, the release checklist, and the full GitHub Actions CI gates: unit tests, both APKs, Lint, and protocol integrity checks
* `Improvement` Centralized build logic into build-logic with an embedded platform-versions plugin source snapshot, so a clean checkout builds as-is
* `Improvement` Removed Android API branches made redundant by minSdk 26

# v0.2.0-m5

###### 2026/08/22

* `Hint` First usable milestone release (covering M1–M5); same-signer host checks, per-capability authorization, disposable worker processes, and fail-closed validation enabled from the start
* `Feature` Standalone Kotlin source compile/run plugin established: embedded Kotlin/JVM 2.3.21 and D8 8.13.17, with compilation and execution in separate processes
* `Feature` Implemented the Protocol 1.1 single-file source profile: package and entry-class analysis, sanitized diagnostics, and complete failure-phase mapping
* `Feature` Implemented four individually authorized host capabilities: app launch, console stream, sleep, and toast
* `Feature` Implemented the authenticated compilation cache with telemetry
