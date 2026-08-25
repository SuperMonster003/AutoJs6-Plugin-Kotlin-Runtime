# Release checklist

Use this checklist for `0.3.0-m6` and later releases. Run local Gradle verification with
`--offline` after dependencies have been warmed once. A release is not complete until every
applicable box is checked and the CI run for the release commit is green.

## 1. Prepare the release commit

- [ ] Start from the intended release branch and confirm `git status --short --branch` contains
  only the reviewed release changes.
- [ ] Set `VERSION_NAME` in `version.properties` to the release name, for example `0.3.0-m6`.
- [ ] Increment `VERSION_BUILD` to a positive value greater than every previously published APK.
  `VERSION_NAME` and `VERSION_BUILD` must change in the same release commit.
- [ ] Do not hand-edit `BuildConfig.VERSION_*`; the Android build reads both values through the
  repository build logic.
- [ ] Move the relevant entries from `CHANGELOG.md`'s `Unreleased` section to a dated release
  section and leave a new empty `Unreleased` section.
- [ ] Confirm `README.md`, `ROADMAP.md`, and this checklist describe the selected milestone and
  actual build commands.

## 2. Verify frozen inputs and source quality

Run from the repository root:

```powershell
.\gradlew.bat :app:verifyPinnedInputs :app:verifyPinnedInputsFailurePath :app:verifyPinnedInputsWiring --offline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --offline
.\gradlew.bat :app:lintDebug --offline
```

- [ ] `verifyPinnedInputs` accepts all three AARs and the lock file.
- [ ] `verifyPinnedInputsFailurePath` proves a one-byte AAR change is rejected for the expected
  digest-mismatch reason.
- [ ] `verifyPinnedInputsWiring` proves both assemble variants directly depend on the verifier.
- [ ] Unit tests, debug/release assembly, Android test APK assembly, and Lint all exit with code 0.
- [ ] Lint reports no new issues; existing entries may only be changed through a reviewed
  `updateLintBaselineDebug` diff.
- [ ] The platform-version snapshot tests pass:

```powershell
.\gradlew.bat -p build-logic/platform-versions test --offline
```

## 3. Verify host-aligned signing

Both build variants load the ignored `sign.properties` file. It must define `storeFile`,
`storePassword`, `keyAlias`, and `keyPassword`; `storeFile` is resolved relative to `app/`.

- [ ] Build from trusted signing material only; never add `sign.properties`, a keystore, or a
  password to Git.
- [ ] Resolve the SDK from `ANDROID_SDK_ROOT` or `ANDROID_HOME`, locate `apksigner`, and run:

```powershell
$androidSdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
$apksigner = "$androidSdk\build-tools\36.0.0\apksigner.bat"
$hostApk = "<path-to-trusted-AutoJs6.apk>"
& $apksigner verify --verbose --print-certs $hostApk
& $apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
& $apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

- [ ] Verification succeeds for all three APKs.
- [ ] The signer `certificate SHA-256 digest` (shown as `V2 Signer` by current build tools) is
  byte-for-byte identical for AutoJs6, debug, and release APKs.
- [ ] Do not publish the GitHub Actions debug artifact: CI deliberately uses an ephemeral
  certificate and that APK cannot satisfy the production same-signer bridge.

## 4. Check APK and schema-v2 metadata

Use `apkanalyzer` from Android SDK command-line tools:

```powershell
$androidSdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
$apkanalyzer = "$androidSdk\cmdline-tools\latest\bin\apkanalyzer.bat"
& $apkanalyzer manifest application-id app/build/outputs/apk/release/app-release.apk
& $apkanalyzer manifest version-name app/build/outputs/apk/release/app-release.apk
& $apkanalyzer manifest version-code app/build/outputs/apk/release/app-release.apk
& $apkanalyzer manifest min-sdk app/build/outputs/apk/release/app-release.apk
& $apkanalyzer manifest target-sdk app/build/outputs/apk/release/app-release.apk
```

- [ ] Application ID is `io.github.supermonster003.autojs6.plugin.kotlin.runtime`.
- [ ] APK version name/code match `VERSION_NAME`/`VERSION_BUILD`; minSdk is 26 and targetSdk is 36.
- [ ] The official-index schema-v2 literals still resolve to:

| Field | Expected value |
|---|---|
| engine | `jvm-source` |
| provider ID | `kotlin-jvm` |
| variant/backend | `kotlin-jvm-d8` |
| task | `jvm-source` |
| protocol min/max | `1.1` / `1.1` |
| required host version | `5276` |
| runtime component | `io.github.supermonster003.autojs6.plugin.kotlin.runtime/org.autojs.plugin.jvmsource.kotlin.service.JavaSourceCompilerService` |

- [ ] The manifest exports exactly the signature-protected `org.autojs.plugin.INFO` and
  `org.autojs.plugin.JVM_SOURCE` services; the worker service remains non-exported.
- [ ] Validate the release entry against the current AutoJs6 Official Plugins Index schema-v2
  validator before publishing metadata.

## 5. Device smoke test

```powershell
$serial = "<adb-serial>"
adb -s $serial install -r app/build/outputs/apk/release/app-release.apk
adb -s $serial shell dumpsys package io.github.supermonster003.autojs6.plugin.kotlin.runtime
```

- [ ] `adb install -r` succeeds without a signature conflict.
- [ ] AutoJs6 discovers both plugin services and selects the Kotlin provider for Kotlin source.
- [ ] Run `samples/m5-capabilities.kt` through the production host/provider path: app launch,
  stdout/stderr streaming, cancellable sleep, and toast all behave as expected.
- [ ] Confirm `RemoteJvmScriptContextTest.everyUngrantedCapabilityFailsBeforeAnyHostDispatch`
  rejects app launch, console stream, sleep, and toast when each capability is absent. Protocol 1.1
  production requests grant the provider's negotiated capability set and expose no per-capability
  user toggle, so this denial matrix belongs at the request boundary rather than in the UI smoke.
- [ ] Run the sample twice and record a materially faster second end-to-end execution. Protocol 1.1
  intentionally exports no cache-specific result field; exact hit/miss, authentication, and
  telemetry semantics remain enforced by the provider cache unit-test suite.
- [ ] Cancel one running request and confirm the disposable worker retires after the grace period
  with no private session workspace left behind.

## 6. Verify the source archive and publish

The signing files are intentionally absent from `git archive`; copy trusted local signing inputs
into the extracted test tree before running the full three-command build.

```powershell
$releaseTag = "v0.3.0-m6"
$archiveRoot = Join-Path ([IO.Path]::GetTempPath()) "autojs6-kotlin-runtime-$([guid]::NewGuid())"
New-Item -ItemType Directory -Path $archiveRoot | Out-Null
git archive --format=zip --output (Join-Path $archiveRoot "$releaseTag.zip") $releaseTag
Expand-Archive (Join-Path $archiveRoot "$releaseTag.zip") (Join-Path $archiveRoot "source")
Copy-Item -LiteralPath sign.properties -Destination (Join-Path $archiveRoot "source/sign.properties")
Copy-Item -LiteralPath app/sm003.jks -Destination (Join-Path $archiveRoot "source/app/sm003.jks")
Push-Location (Join-Path $archiveRoot "source")
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --offline
Pop-Location
```

- [ ] The archive contains the frozen Protocol AARs, platform-version source snapshot, Gradle
  wrapper, version catalog, Lint baseline, changelog, and release checklist.
- [ ] The extracted archive builds offline without any sibling AutoJs6 or Maven Local artifact.
- [ ] Commit the final reviewed release state and wait for the push CI workflow to turn green.
- [ ] Create an annotated tag only after that commit is immutable:

```powershell
git tag -a v0.3.0-m6 -m "AutoJs6 Kotlin Runtime Plugin 0.3.0-m6"
git show --no-patch --decorate v0.3.0-m6
git push origin v0.3.0-m6
```

- [ ] For a private milestone, push the host-signed release APK checksum and release evidence only
  to the private repository. Public APK distribution and the schema-v2 index update may remain
  deferred until the project visibility decision is revisited.
