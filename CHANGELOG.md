# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with milestone suffixes.

## [Unreleased]

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
