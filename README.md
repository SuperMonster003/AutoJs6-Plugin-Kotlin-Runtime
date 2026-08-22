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
package smoke.m4

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("M5 Kotlin log")
        context.toast("M5 Kotlin toast")
        context.sleep(500L)
        context.console().error("M5 Kotlin error after sleep")
        return 5
    }
}
```

`console().log/error` is streamed line by line while the worker is running. `sleep` is interrupted by
session cancellation, and `toast` is an explicitly granted host bridge capability.

## Versioning

`VERSION_NAME` follows SemVer with an optional milestone suffix (currently `0.2.0-m5`), while
`VERSION_CODE` is a positive, monotonically increasing Android package version. Release metadata
declares engine `jvm-source`, provider ID `kotlin-jvm`, variant `kotlin-jvm-d8`, Protocol 1.1, and
required host version code 5276 for schema-v2 official-index generation.

## Local build

The repository uses frozen AARs from `protocol/` and can build without a sibling AutoJs6 checkout.
Release/debug APKs must be signed with the same certificate as AutoJs6. Local signing material is
expected at the ignored files `sign.properties` and `app/sm003.jks`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --offline
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Install with `adb -s <serial> install -r`. Java Runtime and Kotlin Runtime may remain installed and
enabled together: each compiler service declares its source language, and AutoJs6 keeps a separate
exact-component selection for each language.

## Discovery

The APK provides two signature-protected services:

- `org.autojs.plugin.INFO` for Plugin Center metadata;
- `org.autojs.plugin.JVM_SOURCE` for compilation/execution sessions.

AutoJs6 discovers both by action. It does not depend on this plugin's package name.
