# AutoJs6 Kotlin Runtime Plugin

Independent Kotlin single-file compiler/runtime provider for AutoJs6.

The plugin exposes `org.autojs.plugin.JVM_SOURCE`, compiles Kotlin source with the pinned
Kotlin/JVM compiler 2.3.21 at JVM target 1.8, converts verified class output with D8 8.13.17, and
executes the resulting DEX in a disposable worker process. The compiler and worker never run inside
the AutoJs6 process.

## Compatibility

- Application ID: `io.github.supermonster003.autojs6.plugin.kotlin.runtime`
- Minimum Android API: 26 (Kotlin 2.3.21 compiler bytecode uses `MethodHandle`, which Android/D8
  supports from Android 8.0)
- Required AutoJs6 version code: 5276
- JVM source protocol: 1.1
- Entry API: 2 (`AutoJsJvmEntry.run(JvmScriptContext)`)
- Current AutoJs6 host source shape: one `.kt` file normalized to `Main.kt`, entry simple name
  `Main`, and an optional ordinary ASCII package plus imports

## Source example

The ready-to-run [M5 capability sample](samples/m5-capabilities.kt) demonstrates the complete first
capability set. The editor file name may be arbitrary, but the current AutoJs6 Protocol 1.1 host
normalizes it to `Main.kt` with entry simple name `Main`:

```kotlin
package samples.m5

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("M6_CAPABILITIES_START")
        val appLaunchSucceeded = context.app().launch("org.autojs.autojs6")
        context.console().log("M6 Kotlin log: appLaunch=$appLaunchSucceeded")
        context.toast("M6 Kotlin toast")
        context.sleep(500L)
        context.console().error("M6_CAPABILITIES_STDERR")
        context.console().log("M6_CAPABILITIES_OK appLaunch=$appLaunchSucceeded")
        return appLaunchSucceeded
    }
}
```

`console().log/error` is streamed line by line while the worker is running. `sleep` is interrupted by
session cancellation, while `app().launch` and `toast` cross the explicitly allowlisted host bridge.
The companion [cancellation sample](samples/m6-cancellation.kt) and AutoJs6-side
[stop helper](samples/m6-host-cancel.js) are intended for device stop/worker-retirement smoke tests.

The provider validates the actual `sourceFileName` and fully qualified `entryClassName` request
fields, so another compliant caller may use a consistent alternative ordinary ASCII simple name.
Backtick-escaped and non-ASCII package identifiers are rejected explicitly in Protocol 1.1 rather
than being misdiagnosed as a default-package entry failure. See the
[source-layout decision](docs/decisions/source-layout.md) for the exact boundary.

## Versioning

`VERSION_NAME` follows SemVer with an optional milestone suffix (currently `0.6.0-m9`), while
`VERSION_BUILD` is a positive, monotonically increasing Android package version. Release metadata
declares engine `jvm-source`, provider ID `kotlin-jvm`, variant `kotlin-jvm-d8`, Protocol 1.1, and
required host version code 5276 for schema-v2 official-index generation.

## Local build

The repository uses frozen AARs from `protocol/` and can build without a sibling AutoJs6 checkout.
The platform-version decision plugin is included as a pinned source snapshot, so a clean checkout
does not require the maintainer's Maven Local repository. JDK 21 is recommended; the Android SDK
must provide platforms 24 and 36.

Release/debug APKs must be signed with the same certificate as AutoJs6. Local signing material is
expected at the ignored files `sign.properties` and `app/sm003.jks`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --offline
.\gradlew.bat :app:lintDebug :m7-harness:assembleDebug :m7-harness:lintDebug --offline
.\gradlew.bat :app:verifyPinnedInputs :app:verifyPinnedInputsFailurePath :app:verifyPinnedInputsWiring --offline
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Install with `adb -s <serial> install -r`. Java Runtime and Kotlin Runtime may remain installed and
enabled together: each compiler service declares its source language, and AutoJs6 keeps a separate
exact-component selection for each language.

GitHub Actions uses a short-lived, generated certificate for its debug artifact. That APK is useful
for build inspection only and cannot replace the host-aligned APK required for AutoJs6 integration.

## M8 source and diagnostic contract

M8 keeps Kotlin 2.3.21, D8 8.13.17, and script JVM target 1.8. A higher build JDK does not change the
validated bytecode contract: class-file major versions above Java 8 remain rejected before D8. The
[JVM-target decision](docs/decisions/jvm-target.md) records the target-11/17 prerequisites, and the
[compiler-upgrade SOP](docs/decisions/kotlin-compiler-upgrade.md) makes every future compiler or D8
change pass the shape-locked patch, runtime verification, full offline/archive suite, and device
execution gates.

Compiler diagnostics retain actionable K2 text plus a source file, line, and column only when the
reported path canonicalizes to the private user source. A leading UTF-8 BOM is stripped before
compilation without shifting the visible first-line coordinate. Complete Protocol diagnostics are
budgeted in UTF-8 and truncated only at Unicode code-point boundaries. The bilingual
[intentional error samples](samples/errors/README.md) cover a missing import, type mismatch, missing
`AutoJsJvmEntry`, and package/request mismatch with their expected failure codes and remedies.

## Script runtime matrix

M9 adds one deliberately pinned library rather than a general dependency resolver. The supported
source/D8 surface is the following exact profile:

| Surface | Script availability | Version/profile | Boundary |
| --- | --- | --- | --- |
| Android framework | Yes | class-only API 24 compiler stubs | Runtime behavior still depends on the device API; host actions remain capability checked. |
| AutoJs6 JVM Entry API | Yes | Entry API 2 / Protocol 1.1 | `JvmScriptContext` is the only supported host bridge. |
| Kotlin stdlib | Yes | 2.3.21 | Pinned with the embedded compiler. |
| `kotlinx-coroutines-core-jvm` | Yes | 1.11.0 | Structured scopes plus `Default`, `IO`, and `Unconfined` are supported. |
| `kotlinx-coroutines-android` / `Dispatchers.Main` | No | Not packaged as a script module | No worker UI/Looper contract; dispatching to Main fails because no Main dispatcher is installed. |
| Full `kotlin-reflect` extensions | No | Not on the script classpath | Basic stdlib class literals remain; `kotlin.reflect.full.*` is unsupported. |
| Serialization, coroutine debug/test, compiler APIs/plugins | No | Not controlled | No arbitrary Maven or transitive script dependencies are resolved. |

The ready-to-run [coroutine sample](samples/coroutines.kt) demonstrates `runBlocking`, structured
`async`, `Dispatchers.Default`, `delay`, and host-cancellation polling. Host cancellation interrupts
the worker entry thread; the `runBlocking` contract cancels its structured children, while the
worker grace-period hard kill remains the fallback for uncooperative or unstructured code. Do not
use `GlobalScope` or expect coroutines to survive `AutoJsJvmEntry.run`—the worker process is retired
after every execution. The exact artifact hash, cancellation mapping, rejected-module rationale,
and cache-invalidation boundary are recorded in the
[controlled-runtime decision](docs/decisions/controlled-runtime-libraries.md).

## M7 performance and stability baseline

On the designated Sony XQ-AT72 / Android 12 device, five identical cache-cold compilations had a
577 ms median and five authenticated cache hits had a 46 ms median (about 12.5x faster). Median
execution time was 30 ms in both groups. The corresponding telemetry was exactly 5 hits, 5 misses,
5 publications, and 0 failures. A separate 50-session release mix reported 44 hits, 6 misses,
1 publication, and 0 cache failures, matching the requested source sequence.

The signed M7 release APK is 30.38 MB, down 32.99% from the M6 45.34 MB baseline and below the
40 MB release budget. R8/resource shrinking remain disabled after an explicit compatibility
evaluation; the safe reduction comes from embedding a deterministic class-only API 24 compiler
classpath instead of the full platform archive.

The compiler is retained only for one host binding epoch so warm cache hits remain fast. Kotlin's
application environment is disposed after every invocation, and the dedicated `:compiler` process
retires on final unbind. Plugin metadata lives in `:discovery`, while every execution still gets a
fresh single-use `:worker` process. See the
[size baseline](docs/perf/size-baseline.md),
[latency benchmark](docs/perf/compile-latency.md),
[compiler lifecycle decision](docs/decisions/compiler-lifecycle.md), and
[stress/fault record](docs/perf/stress-and-faults.md) for measurements and reproduction commands.

Run the complete device suite against a connected, host-aligned test device with:

```powershell
.\scripts\stress\run-m7-device.ps1 -Serial <adb-serial>
```

See [ROADMAP.md](ROADMAP.md), [CHANGELOG.md](CHANGELOG.md), the
[release checklist](docs/RELEASE_CHECKLIST.md), and the
[0.6.0-m9 verification record](docs/releases/0.6.0-m9.md) for the current private release evidence.
The prior source/diagnostic milestone remains captured in the
[0.5.0-m8 verification record](docs/releases/0.5.0-m8.md).

## Discovery

The APK provides two signature-protected services in separate auxiliary processes:

- `org.autojs.plugin.INFO` for Plugin Center metadata in `:discovery`;
- `org.autojs.plugin.JVM_SOURCE` for compilation/execution sessions in `:compiler`.

AutoJs6 discovers both by action. It does not depend on this plugin's package name.
