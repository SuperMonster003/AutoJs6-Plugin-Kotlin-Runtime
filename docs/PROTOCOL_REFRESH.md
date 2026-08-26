# Frozen Protocol refresh SOP

This procedure refreshes the three AutoJs6 AAR inputs consumed by the independent Kotlin Runtime.
It is intentionally staging-first: no generated file overwrites `protocol/` until provenance,
content and compatibility have been reviewed together.

## 1. Frozen set

The lock is atomic. A refresh always treats these files as one set:

| Plugin file | Host task | Host output |
|---|---|---|
| `common-plugin-api.aar` | `:plugin-api:common-plugin-api:bundleDebugAar` | `plugin-api/common-plugin-api/build/outputs/aar/common-plugin-api-debug.aar` |
| `protocol-wire-api.aar` | `:plugin-api:protocol-wire-api:bundleDebugAar` | `plugin-api/protocol-wire-api/build/outputs/aar/protocol-wire-api-debug.aar` |
| `jvm-source-api.aar` | `:plugin-api:jvm-source-api:bundleDebugAar` | `plugin-api/jvm-source-api/build/outputs/aar/jvm-source-api-debug.aar` |

The `debug` variant is deliberate: it is the variant from which the current snapshots were
created. The variant, exact tasks and exact outputs are mandatory schema-2 lock fields so a future
refresh cannot silently substitute `bundleReleaseAar`.

## 2. Preconditions

1. Select a full 40-character AutoJs6 source commit. For an actual promotion, it must be reachable
   from an access-controlled remote or immutable host tag.
2. Use a clean detached worktree for that commit. Do not build from the developer's active host
   working tree, even if unrelated changes appear harmless.
3. Confirm `git status --porcelain=v1 --untracked-files=all` is empty before generation.
4. Ensure the expected JDK, Android SDK and already-cached Gradle dependencies are available.
5. Run Gradle with `--offline`; protocol refresh must not double as a dependency-upgrade window.
6. Review host changes to all three modules since the previous lock revision. An apparently
   unrelated common metadata change still requires rebuilding the complete set.

Example clean worktree creation:

```powershell
$host = "D:\idea-projects\AutoJs6"
$revision = "<full-clean-host-commit>"
$worktree = Join-Path ([System.IO.Path]::GetTempPath()) "autojs6-protocol-$($revision.Substring(0, 12))"
git -C $host worktree add --detach $worktree $revision
git -C $worktree status --short --branch
```

Do not reuse or recursively remove a path unless its resolved absolute path has first been checked
to be the intended temporary worktree.

## 3. Stage the generated AARs

Run the repository helper from the plugin root:

```powershell
.\scripts\protocol\stage-protocol-refresh.ps1 `
  -HostRepository $worktree `
  -ExpectedRevision $revision
```

The helper:

- requires an exact clean host commit before and after the build;
- invokes only the schema-2 lock's three `bundleDebugAar` tasks;
- passes `--offline --no-daemon --console=plain`;
- validates that every generated AAR is a readable ZIP containing `classes.jar`;
- copies artifacts only into `build/protocol-refresh/<revision>`;
- records generated/pinned size and SHA-256 in `refresh-report.json`;
- always records `promotionPerformed=false` and never edits `protocol/` or the lock.

`-SkipBuild` is permitted only when reviewing outputs generated moments earlier in the same clean
worktree. It is not valid release evidence by itself.

## 4. Review before promotion

Review all of the following:

1. `git diff <old-revision>..<new-revision> -- plugin-api/common-plugin-api
   plugin-api/protocol-wire-api plugin-api/jvm-source-api`;
2. top-level AAR entry names, sizes and SHA-256;
3. `classes.jar` API surface with `javap`, including `JvmSourceContract`, messages, validation,
   codec and Entry API interfaces;
4. packaged AIDL and consumer rules;
5. protocol/Entry API/schema versions and old/new negotiation behavior;
6. all new required tagged fields, enum values and payload limits;
7. host module unit tests and provider conformance results.

AAR byte differences alone are not proof of an API change. ZIP metadata, build variants and line
endings can change the outer digest. Conversely, identical public signatures are not sufficient
when codec validation or AIDL semantics changed. Both binary identity and semantic review are
required.

For text-only discrepancies, compare decoded UTF-8 after normalizing CRLF/CR to LF, but never use a
normalized digest as the lock digest. The lock always stores SHA-256 of the exact committed AAR.

## 5. Promote atomically

Only after review:

1. copy all three staged AARs into `protocol/` in one change set;
2. update `protocol-artifacts.lock.json`:
   - `sourceRevision` is the exact clean host commit;
   - `sourceDirty` remains the JSON boolean `false`;
   - `snapshotDate` is the promotion date;
   - `refreshRehearsalDate` is the last successfully exercised SOP date;
   - `artifactVariant`, `sourceModule`, `sourceTask` and `sourceOutput` match the generated files;
   - every `sha256` is lowercase and matches the exact committed AAR;
3. update `protocol/README.md`, protocol/Entry API documentation and compatibility fixtures;
4. commit AARs, lock, code and tests together. Never submit an AAR-only or lock-only refresh.

Then run:

```powershell
.\gradlew.bat :app:verifyPinnedInputs `
  :app:verifyPinnedInputsFailurePath `
  :app:verifyPinnedInputsWiring `
  --offline --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  :app:assembleDebug `
  :app:assembleRelease `
  :app:assembleDebugAndroidTest `
  :m7-harness:assembleDebug `
  :app:lintDebug `
  :m7-harness:lintDebug `
  --offline --console=plain
```

The metadata failure path proves `sourceDirty=true` is rejected. The corruption path proves that
one appended byte in any pinned AAR is rejected. Assembly wiring proves debug and release cannot
bypass the verifier.

## 6. Compatibility and device gates

For a protocol minor update, the release cannot proceed until tests cover:

- old host plus old provider;
- old host plus a new provider whose range includes the old minor;
- old host plus a new-minor-only provider, which must fail cleanly;
- new host plus old provider, negotiating the old minor;
- new host plus a dual-range provider, negotiating the new minor;
- unknown optional fields accepted and unknown required fields rejected;
- every new capability's authorization, payload, dispatch and response stages;
- same-signer real-device success, cancellation, tampering and process retirement.

Actual host capabilities also require the Java provider to pass the same matrix. A Kotlin-only
green result is insufficient for a shared `jvm-source-api` release.

## 7. M10 rehearsal record — 2026-08-26

The SOP was exercised without promoting a new protocol:

| Field | Evidence |
|---|---|
| Source commit | `d21c69a2523a529ce6e2cd5d7dc3ced49cbaf74d` |
| Source state before/after build | clean detached worktree; `sourceDirty=false` |
| Build mode | offline, Gradle 9.6.1, JDK 21, AGP 9.3.0, Kotlin plugin 2.3.21 |
| Correct tasks | all three `bundleDebugAar`; 78/78 tasks completed |
| Incorrect-variant probe | all three `bundleReleaseAar` completed but produced different AARs and were rejected as refresh inputs |
| `common-plugin-api.aar` | exact outer SHA-256 match |
| All three `classes.jar` files | exact byte-for-byte SHA-256 match |
| Wire AAR difference | only `proguard.txt` CRLF versus LF; LF-normalized bytes matched |
| JVM AAR differences | only five packaged AIDL files and `proguard.txt` CRLF versus LF; every LF-normalized entry matched |
| Promotion | deliberately not performed; public API, AIDL semantics and frozen input bytes remain unchanged |

Generated debug hashes observed in the Windows checkout were:

| File | Generated bytes | Generated SHA-256 | Frozen SHA-256 |
|---|---:|---|---|
| `common-plugin-api.aar` | 10,882 | `d745bb24d6a6995e68ebea27d592faec162f0bad4f9bf70cb038a480ad8c6df2` | same |
| `protocol-wire-api.aar` | 30,717 | `6c597ec095852eac7e6277574191141100dc96b6c5a803725ed540f42aa7d871` | `a850d2d649638e9131b68a0a3806c950e6be8aa0fa5c5f1e7a575c9260ebf202` |
| `jvm-source-api.aar` | 156,174 | `e44ee42e5147e37ba3d58127146faa361a0b58107b8991c214ea5bd39d2eb49a` | `87615dc0d7f678e13d6823fd1d4ad226dc5f04a2599ae93700882577d7bb7e2e` |

This rehearsal found and closed the previously undocumented build-variant ambiguity. It also
showed why an outer-hash mismatch must be reviewed instead of automatically promoted.

The legacy source commit was not reachable from a configured host remote during the rehearsal.
That does not alter the already-frozen binary identity, but a future actual protocol promotion
must use a remotely reachable clean commit or immutable host tag. Uploading the developer's
hundreds of unrelated local host commits is explicitly outside this SOP.

## 8. Cleanup

After evidence is captured, remove only the exact temporary worktree through the source repository:

```powershell
git -C $host worktree remove $worktree
git -C $host worktree prune
```

Confirm that the active AutoJs6 worktree status is unchanged and that no signing material or host
build output was copied into the plugin repository. `build/protocol-refresh/` is generated staging
data and must remain untracked.

