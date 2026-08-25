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
- Current source shape: one `.kt` file, entry simple name `Main`, optional package/imports

## Source example

The ready-to-run [M5 capability sample](samples/m5-capabilities.kt) demonstrates the complete first
capability set. The file name may be arbitrary, but the entry simple name remains `Main` in
Protocol 1:

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

## Versioning

`VERSION_NAME` follows SemVer with an optional milestone suffix (currently `0.3.0-m6`), while
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
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --offline
.\gradlew.bat :app:assembleDebugAndroidTest --offline
.\gradlew.bat :app:lintDebug --offline
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

See [ROADMAP.md](ROADMAP.md), [CHANGELOG.md](CHANGELOG.md), and the
[release checklist](docs/RELEASE_CHECKLIST.md) for milestone status and release gates.

## Discovery

The APK provides two signature-protected services:

- `org.autojs.plugin.INFO` for Plugin Center metadata;
- `org.autojs.plugin.JVM_SOURCE` for compilation/execution sessions.

AutoJs6 discovers both by action. It does not depend on this plugin's package name.
