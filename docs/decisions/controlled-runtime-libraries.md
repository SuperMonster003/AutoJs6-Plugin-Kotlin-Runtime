# Controlled script runtime libraries

Status: Accept `kotlinx-coroutines-core-jvm` and reject the optional Android/reflect modules for
M9; accepted on 2026-08-26

## Context

Before M9, Kotlin source was compiled and passed to D8 against exactly the class-only Android API
24 surface, Entry API 2, and Kotlin stdlib 2.3.21. The disposable worker's application class loader
also contains implementation dependencies used by the provider, but those internals are not a
supported script contract. A library becomes a controlled script runtime only when its pinned
artifact is deliberately present in all of these places:

1. the compiler classpath used for user source;
2. the D8 library set used for user bytecode;
3. the worker APK runtime;
4. the ordered, hashed runtime-library and toolchain fingerprints.

The M9 question was whether structured concurrency is useful enough to widen that contract without
adding Android UI semantics or a general dependency mechanism.

## Decision: add coroutine core only

Add the exact JVM binary `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0` as the fourth
controlled artifact. The one-time dependency window resolved this binary:

- file: `kotlinx-coroutines-core-jvm-1.11.0.jar`;
- uncompressed artifact size: 1,577,052 bytes;
- SHA-256: `d1d75aa01dffbb4d1c520e67e4c4e7f5f6174718e7cb4632412503f2f0e604fa`.

The build verifies the name and digest before copying it to the private compiler-classpath asset.
The asset has a stable provider-owned name, `kotlinx-coroutines-core-jvm.jar`; no transitive or
floating dependency enters the controlled set. `CompilerClasspath`, `D8RuntimeLibraries`, and
provider capability negotiation use the same ordered four-file identity.

`runBlocking`, `coroutineScope`, `launch`, `async`, `delay`, and the core dispatchers are supported.
`Dispatchers.Default`, `Dispatchers.IO`, and `Dispatchers.Unconfined` can create or use threads even
though entry invocation begins on the worker service's one execution thread. This is acceptable
because every execution still has its own single-use Android process. Scripts should keep work in a
structured scope owned by `run`, avoid `GlobalScope`, and must not expect background work to outlive
the entry result or worker process.

## Cancellation mapping

`WorkerCancellation.cancel` records the Protocol reason and interrupts the entry execution thread.
The official `runBlocking` JVM contract cancels its job and throws `InterruptedException` when that
blocked thread is interrupted. Consequently, a structured child running on `Dispatchers.Default`
is cancelled through its parent and the provider reports the original Protocol cancellation reason
at phase `EXECUTION`.

This is a cooperative path, not a replacement for process isolation. Code that swallows
interruption, runs a non-suspending infinite loop on another dispatcher, or launches unstructured
work may not finish cooperatively. The existing cancellation grace and mandatory worker hard-retire
remain the final boundary. The M9 same-signer device case proves both a normal structured result and
host cancellation after a `Dispatchers.Default` child has reached the host bridge.

## Rejected modules

Do not add `kotlinx-coroutines-android`. That module's principal contract is an Android
`Dispatchers.Main` backed by a `Handler`; the worker owns no UI and must use the capability-checked
host bridge for host actions. The `Dispatchers.Main` symbol exists in coroutine core, but no Main
dispatcher implementation is installed, so attempting to dispatch to it is unsupported and fails
at runtime. This absence is asserted on a real worker by the M9 harness.

Do not add `kotlin-reflect` to the controlled script compiler/D8 classpath. Basic class literals and
the small `KClass` surface supplied by stdlib remain available, while extensions such as
`kotlin.reflect.full.memberProperties` deliberately do not compile. The APK still packages a
provider-internal reflect version required by the embedded compiler; its physical presence is not a
script API, is not included in the controlled fingerprint, and must not be relied upon by name.
Adding full reflection would widen introspection/reachability, expose an independently versioned
surface, and duplicate a roughly 3.04 MB internal artifact without a concrete M9 script use case.

Serialization libraries, coroutine Android/debug/test modules, compiler APIs, compiler plugins, and
arbitrary Maven dependencies likewise remain outside the script contract.

## Fingerprint and cache consequences

The compiler-classpath digest domain advances from
`org.autojs.jvm-source.kotlin.compiler-classpath.v1` to `.v2`. Changing any controlled artifact now
changes `runtimeLibraryFingerprint`; that changes `JvmToolchainFingerprint`, which is part of every
canonical compilation-artifact cache key. Tests mutate only the coroutine artifact and prove the
runtime and toolchain fingerprints both change, the old installed-classpath identity fails closed,
and the pre-existing cache-key test proves a toolchain change cannot hit an old entry.

This intentionally invalidates all pre-M9 compilation artifacts. It is safer than reusing DEX
compiled against a three-library runtime profile.

## Measured package cost

The canonical M9 release-commit APK is 31,854,266 bytes (31.85 decimal MB), up 1,469,880 bytes or 4.84%
from the canonical M8 baseline of 30,384,386 bytes. It remains 8,145,734 bytes below the 40 MB
release budget. Inside the APK, the controlled coroutine asset occupies 1,577,052 bytes before ZIP
compression and 1,446,189 bytes after compression; this deterministic duplicate asset accounts for
nearly all of the release delta. The APK already contained coroutine classes for embedded compiler
internals before M9, so the supported runtime widening does not require a second DEX copy.

## Evidence and upstream references

- The checked-in [`samples/coroutines.kt`](../../samples/coroutines.kt) is compiled with the real K2
  compiler, converted by the pinned D8, and validated by the unit suite.
- [`ControlledRuntimeLibrariesTest`](../../app/src/test/java/org/autojs/plugin/jvmsource/kotlin/ControlledRuntimeLibrariesTest.kt)
  pins the ordered artifact and fingerprint boundary.
- [`KotlinCompilerPipelineTest`](../../app/src/test/java/org/autojs/plugin/jvmsource/kotlin/KotlinCompilerPipelineTest.kt)
  proves the accepted profile compiles/D8s and the rejected reflect/Android modules do not compile.
- [kotlinx.coroutines 1.11.0 release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)
- [`runBlocking` API contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html)
- [Coroutine Android module API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-android/)
- [Kotlin reflection documentation](https://kotlinlang.org/docs/reflection.html)
