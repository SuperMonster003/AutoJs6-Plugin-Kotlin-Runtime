# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with milestone suffixes.

## [Unreleased]

## [0.6.0-m9] - 2026-08-26

### Added

- Added the exact `kotlinx-coroutines-core-jvm` 1.11.0 binary as a verified fourth controlled
  script artifact, with a pinned SHA-256 digest and no transitive dependency resolution.
- Added a checked-in coroutine/cancellation sample that is compiled by the real K2 compiler and
  converted by the pinned D8 during unit tests.
- Added a same-signer Android harness case for structured `Dispatchers.Default` execution,
  deliberate `Dispatchers.Main` absence, Protocol cancellation propagation, and worker retirement.
- Added the controlled-runtime decision and README runtime matrix covering supported core APIs,
  rejected Android/reflect modules, concurrency boundaries, cancellation, and hard-kill fallback.

### Changed

- Expanded the compiler/D8 runtime identity from API 24 stubs, Entry API, and stdlib to the same
  ordered set plus coroutine core, and advanced the classpath fingerprint domain to v2.
- Bound the coroutine artifact identity into runtime/toolchain fingerprints and canonical
  compilation-cache keys so every pre-M9 artifact is invalidated.
- Advanced the package to `0.6.0-m9` / build 6 while retaining Kotlin 2.3.21, D8 8.13.17, and script
  JVM target 1.8.

### Security

- Kept `kotlinx-coroutines-android`, full `kotlin-reflect`, serialization, compiler APIs/plugins,
  and arbitrary Maven dependencies outside the supported script classpath.
- Preserved a new disposable process for every execution and the existing hard-retire fallback;
  structured coroutine cancellation supplements rather than weakens the process boundary.

## [0.5.0-m8] - 2026-08-25

### Added

- Added executable source-ingress boundaries at exactly 4 MiB and one byte beyond, expanded entry
  analysis across multiple top-level classes, nested same-name classes, object/interface/abstract
  shapes, and pinned the current Java 8 class-file ceiling.
- Added real K2 BOM/line/column snapshots, Chinese and supplementary Unicode diagnostic-budget
  boundaries, and four bilingual checked-in failure fixtures that are compiled or analyzed by the
  unit suite.
- Added accepted decisions for the Protocol 1.1 source layout, retention of JVM target 1.8, and the
  mandatory Kotlin compiler `patch → verify → full test` upgrade SOP.
- Extended the same-signer Android harness with an M8 Binder-path case for alternative entry names,
  BOM diagnostics, ASCII-package rejection, package mismatch, and missing entry-interface messages.

### Changed

- Kept the current AutoJs6 host profile at `Main.kt` / `<ASCII package>.Main`, while explicitly
  preserving the provider's ability to honor a consistent alternative ASCII source/entry name from
  Protocol 1.1 request fields.
- Replaced ambiguous unsupported-package failures with stable, actionable public messages and made
  Kotlin entry failure text consistently refer to Kotlin rather than Java.
- Advanced the package to `0.5.0-m8` / build 5 while deliberately retaining Kotlin 2.3.21, D8
  8.13.17, and script JVM target 1.8.

### Fixed

- Preserved valid K2 source positions when Windows path separators differ between the compiler and
  provider, while continuing to discard every location that does not canonicalize to the private
  source file.
- Verified that complete encoded diagnostics truncate only at Unicode code-point boundaries, never
  leaving half of an emoji or malformed UTF-8.

### Security

- Allowed only predefined, pre-sanitized source-policy messages to override generic terminal text;
  arbitrary exceptions, private paths, digests, process identities, and compiler internals remain
  behind existing redaction and stable fallbacks.

## [0.4.0-m7] - 2026-08-25

### Added

- Added a same-signer Android M7 harness that drives Protocol 1.1 through the real AutoJs6 process
  for five-pair cold/warm benchmarks, 50-session release/debug stress runs, resource snapshots, and
  four contract-level fault injections.
- Added PowerShell and POSIX-shell one-command device runners that build offline, install the
  release/debug variants in sequence, capture structured evidence, verify process retirement, and
  restore the signed release APK.
- Added reproducible APK-size, compile-latency, compiler-lifecycle, and stress/fault evidence under
  `docs/perf/` and `docs/decisions/`, plus exact telemetry regression vectors for benchmark and
  stress source sequences.
- Extended CI to assemble and Lint the M7 harness alongside the existing application gates.

### Changed

- Replaced the embedded full API 24 platform archive with a deterministic class-only compiler
  classpath, reducing the release APK from 45.34 MB to 30.38 MB (32.99%) without changing the
  compile/runtime contract.
- Moved metadata discovery to a dedicated `:discovery` process and made the compiler process
  binding-scoped: Kotlin application state is disposed after each invocation and the dedicated
  `:compiler` process retires on final unbind or after the existing critical-session grace period.
- Centralized the shared AndroidX instrumentation dependencies in the version catalog and removed
  the now-obsolete Lint baseline entry; both the app and M7 harness report zero new issues.
- Advanced the package to `0.4.0-m7` / build 4.

### Fixed

- Prevented repeated cache-disabled compiler invocations from retaining Kotlin compiler application
  state across host binding epochs; the final 50-session resource audit kept workspaces at zero,
  file descriptors stable, and total PSS growth within the documented budget.

### Security

- Kept R8 and resource shrinking disabled after compatibility evaluation showed that the embedded
  compiler would require broad missing-JDK suppressions. Debug-only kill/snapshot receivers are
  signature-protected and excluded from the release manifest.

## [0.3.0-m6] - 2026-08-25

### Added

- Added the M6 engineering roadmap and a release checklist covering version synchronization,
  host-aligned signing, device smoke tests, schema-v2 metadata, source archives, and tags.
- Added a repository-local source snapshot of `org.autojs.build.platform-versions` 1.4.1 so clean
  CI and source archives do not depend on an unpublished Maven Local artifact.
- Added protocol-integrity regressions for normal verification, one-byte AAR corruption, and
  `assembleDebug`/`assembleRelease` task wiring.
- Added GitHub Actions gates for the platform-version plugin tests, protocol integrity, Android
  unit tests, debug and Android-test APK assembly, Lint, and verification artifacts.
- Added an Android Lint baseline and a zero-new-warning gate.
- Added full four-capability device-smoke markers, cancellation and host-stop fixtures, and a
  fail-closed regression that denies each ungranted script capability before host dispatch.

### Changed

- Centralized SDK, JDK, application version, AGP, Kotlin, KSP, and R8 selection in the M6 build
  logic while retaining the frozen runtime compiler/D8 coordinates.
- Removed Android API branches and `@TargetApi` annotations made obsolete by minSdk 26.
- Marked schema-v2 plugin index strings as externally consumed resources so Lint and future
  resource shrinking preserve them.
- Tuned the private-repository CI path with bounded artifact retention, concurrent-run
  cancellation, a manual trigger, and no duplicate build for annotated tag pushes.
- Made the strict Lint gate reproducible by excluding online latest-version advisories; dependency
  and toolchain upgrades remain explicit build-logic decisions instead of time-varying CI failures.

## [0.2.0-m5] - 2026-08-22

### Added

- **M1 — Independent provider:** established a standalone Android plugin with frozen local
  Protocol AAR inputs, signature-protected discovery services, schema-v2 metadata, and host-aligned
  signing.
- **M2 — Kotlin-to-DEX pipeline:** embedded Kotlin/JVM 2.3.21 and D8 8.13.17, added the audited
  Android bytecode patches, and validated compiler inputs and generated artifacts.
- **M3 — Process and resource isolation:** separated compilation and execution into `:compiler`
  and disposable `:worker` processes, with bounded IPC, cancellation grace, hard retirement,
  private workspaces, read-only DEX loading, and cleanup barriers.
- **M4 — Protocol 1.1 source profile:** added bounded single-file Kotlin ingestion, package and
  entry-class analysis, sanitized diagnostics, observation payloads, and complete failure-phase
  mapping for Entry API 2.
- **M5 — Capabilities and cache:** implemented individually authorized app launch, console stream,
  sleep, and toast bridges; added authenticated compilation caching, invalidation, telemetry, and
  the `samples/m5-capabilities.kt` smoke script.

### Security

- Enforced same-signer host calls, per-request capability grants, exact protocol digests, bounded
  source/output/diagnostic payloads, single-use worker generations, and fail-closed artifact and
  cache validation.
