# Embedded Kotlin compiler upgrade SOP

Status: Accepted for M8 on 2026-08-25

## Context

The provider pins Kotlin compiler/stdlib/script/daemon artifacts at 2.3.21 and patches the compiler
JAR into an Android-headless runtime. This is materially different from updating the Kotlin Gradle
plugin used to build an ordinary app. As of this decision, Kotlin 2.4.10 is the latest stable release
listed by JetBrains, and Kotlin 2.4.0 introduced new language/compiler behavior and Java 26 support:

- [JetBrains Kotlin releases](https://github.com/JetBrains/kotlin/releases)
- [What's new in Kotlin 2.4.0](https://kotlinlang.org/docs/whatsnew24.html)
- [Kotlin release process](https://kotlinlang.org/docs/releases.html)

The existence of a newer stable artifact is a review trigger, not authorization for an automatic
upgrade. M8 retains 2.3.21 because its patched Android runtime and device behavior are already
audited; the diagnostic milestone should not silently change the accepted language/compiler output.

## The mandatory three-part gate

Every Kotlin compiler or D8 version change must complete `patch → verify → full test` in one reviewed
candidate. A failure in any part blocks the version change.

### 1. Patch: shape-lock the replacement artifact

Run `:app:patchKotlinCompilerForAndroid` without relaxing assertions. It must:

- find and transform exactly these four compiler classes:
  `PerformanceManager`, `DefaultJava11Shim`, `PathUtil`, and `KotlinCoreEnvironment$Companion`;
- patch exactly the three admitted `PerformanceManager` methods and verify no
  `java/lang/management` invocation remains there;
- redirect the Java 11 concurrent-long map, resource-root lookup, and extension registration to the
  audited Android compatibility classes;
- verify the exact `META-INF/extensions/compiler.xml` digest;
- relocate exactly the allowlisted `java.awt`, `java.beans`, and `javax.swing` references, failing
  on either a missing expected type or any new unadmitted desktop dependency;
- produce a deterministic, signature-stripped patched JAR.

Do not update the expected class set, descriptor digest, desktop type map, or method descriptors
until the upstream diff and the new call graph have been reviewed. An assertion failure is useful
evidence that the patch must be redesigned, not noise to suppress.

### 2. Verify: prove the packaged runtime and fixed profile

Run at minimum:

```powershell
.\gradlew.bat :app:patchKotlinCompilerForAndroid :app:verifyKotlinCompilerRuntime `
  :app:verifyPinnedInputs :app:verifyPinnedInputsFailurePath :app:verifyPinnedInputsWiring `
  --offline --console=plain
```

Then verify:

- Trove and all Android compatibility support classes/resources are packaged;
- `KotlinCompilerResourceRootTest`, `CompilerArgumentsTest`, and the compiler start/retirement tests
  pass without enabling host JDK, reflection, scripts, compiler plugins, or an uncontrolled classpath;
- the compiler/stdlib versions exposed in provider metadata and `THIRD_PARTY_NOTICES.md` match the
  actual artifacts;
- `sourceCompilerVersion`, toolchain fingerprint, and canonical cache key change, and old cache
  entries degrade to authenticated misses rather than being reused;
- source-error fixtures and BOM line/column snapshots are reviewed for intentional wording or
  position changes.

### 3. Full test: rebuild and execute the distribution

After dependencies have been fetched in one explicit network window, return to offline mode and run:

```powershell
.\gradlew.bat -p build-logic/platform-versions test --offline --console=plain
.\gradlew.bat :app:verifyPinnedInputs :app:verifyPinnedInputsFailurePath `
  :app:verifyPinnedInputsWiring :app:testDebugUnitTest :app:lintDebug `
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest `
  :m7-harness:assembleDebug :m7-harness:lintDebug --offline --console=plain
```

Also rebuild from `git archive`, install the host-aligned release on the designated device, and run
the real Binder compiler → analyzer → D8 → DEX loader → entry invocation path. Re-run the M7 cold/warm
and resource budgets because compiler upgrades can change both cache latency and retained ART memory.
Confirm compiler/worker retirement and repeat at least the M8 source/diagnostic harness case.

## Version coordination and rollback

- Upgrade compiler embeddable, stdlib, script runtime, daemon, metadata, notices, and lock/evidence
  together. Review the separately pinned reflect/coroutines/Trove compatibility artifacts rather
  than assuming their versions should mechanically match.
- Do not combine a Kotlin compiler upgrade with a JVM target, D8, protocol, or runtime-library
  expansion unless the candidate explicitly tests every cross-product and the release notes state
  the combined compatibility change.
- Keep the prior release tag and APK as the rollback point. If patch shape, Android startup, source
  snapshots, D8 output, device memory, or cache isolation regresses, restore the prior coordinates;
  never carry forward a partially patched compiler.

This SOP fulfills the Roadmap's recurring “bytecode patch three-piece” gate. The checklist remains
open on every future version bump and is complete only for the candidate being released.
