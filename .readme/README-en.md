<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Kotlin 2.3.21 single-file source compiler and runner plugin for AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/commit/17f42fa7aa2a2046c74e558f313b7510d155f365"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### Introduction

******

The AutoJs6 Kotlin Runtime plugin lets AutoJs6 compile and run single-file Kotlin source code (`.kt`) directly. It embeds the Kotlin/JVM 2.3.21 compiler (K2) and the D8 8.13.17 bytecode converter, and executes the compiled output in a disposable worker process; neither the compiler nor the script ever runs inside the AutoJs6 process.

This plugin and [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) are sister plugins: they can be installed side by side, each serving Kotlin / Java source respectively, and AutoJs6 remembers the selected compiler component per language.

******

### Features

******

- Provides the `org.autojs.plugin.JVM_SOURCE` compile/execute service and the `org.autojs.plugin.INFO` Plugin Center discovery service, both signature-protected and running in separate auxiliary processes.
- Embeds the Kotlin/JVM 2.3.21 compiler (K2); scripts target JVM 1.8 bytecode, converted to DEX by D8 8.13.17 before execution.
- Supports four individually authorized host capability bridges: live console output `console().log/error`, app launch `app().launch`, interruptible `sleep`, and `toast` messages.
- Supports the Kotlin standard library and `kotlinx-coroutines-core-jvm` 1.11.0 structured concurrency (`Dispatchers.Default` / `IO` / `Unconfined`).
- Authenticated compilation cache: warm hits for identical source have a ~46 ms median vs ~577 ms cold compilation (about 12.5x faster in device benchmarks); execution median is ~30 ms.
- Compilation errors keep the original K2 diagnostics with source line/column positions; BOM headers, Windows path differences, and Chinese/emoji truncation are all handled deterministically.
- Every execution runs in a fresh disposable worker process that is retired afterwards; stopping the script from the host promptly interrupts sleep and coroutines.
- README and CHANGELOG are available in ten languages: Simplified Chinese, Traditional Chinese (HK/TW), English, French, Spanish, Japanese, Korean, Russian, and Arabic.

******

### Quick Start

******

- **Install** — Download the APK from [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) and install it, or build locally as described in the Build section below. Note: the plugin must be signed with the same certificate as AutoJs6; the throwaway-certificate debug APK produced by GitHub Actions is for build inspection only and cannot integrate with a real device host. The AutoJs6 host version code must be at least 5276.
- **Enable** — Running JVM source is currently an experimental AutoJs6 feature: enable the experiment switch in the host, then explicitly select this plugin as the compiler component for the Kotlin language. If either step is missing, running reports the stable error codes `JVM_SOURCE_EXPERIMENT_DISABLED` or `JVM_SOURCE_PROVIDER_NOT_SELECTED` respectively.
- **Run** — Create a `.kt` file in the AutoJs6 editor, write an entry class implementing the `AutoJsJvmEntry` interface, and tap run (see the Usage Example below). The current host normalizes the source name to `Main.kt` with the entry simple name fixed to `Main`; an ordinary ASCII package and import statements are optional.
- **Troubleshoot** — On compilation failure the console shows K2 diagnostics with line/column positions; bilingual walkthroughs of the four common errors (missing import, type mismatch, missing entry interface, package/request mismatch) live in [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). Runtime failures surface only stable error codes (such as `JVM_SOURCE_COMPILE_FAILED`, `JVM_SOURCE_TIMEOUT`) and never leak internal paths.

******

### Usage Example

******

A minimal ready-to-run example demonstrating all four current host capabilities:

```kotlin
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("Hello from Kotlin 2.3.21")
        context.toast("AutoJs6 Kotlin Runtime")
        context.sleep(500L)
        val launched = context.app().launch("org.autojs.autojs6")
        return launched
    }
}
```

`console().log/error` streams line by line while the script is running; `sleep` is promptly interrupted by a stop action. More examples live in the [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples) directory: capability smoke test `m5-capabilities.kt`, cancellation demo `m6-cancellation.kt`, and coroutine example `coroutines.kt`.

******

### Boundaries

******

To keep behavior safe and predictable, the current version deliberately maintains the following boundaries:

- Single-file Kotlin source only, up to 4 MiB; multi-file projects, class files, JARs, and DEX inputs are not supported yet.
- The entry class must implement `AutoJsJvmEntry` (Entry API 2); package names support ordinary ASCII identifiers only — backtick-escaped and non-ASCII packages are rejected explicitly.
- No Maven or third-party dependencies are resolved; the libraries available to scripts are exactly the Script Runtime list below.
- Script bytecode targets JVM 1.8; class files above Java 8 are rejected before D8.
- The worker process retires after every execution, so coroutines and other background work do not survive `run` returning; do not use `GlobalScope`.
- The released protocol is JVM Source Protocol 1.1; Protocol 1.2 is a proposal only — clipboard/document/HTTPS/notification capabilities are not yet available.

******

### Script Runtime

******

The libraries available for script compilation and execution form an exactly pinned allowlist:

#### Available

- Android framework: compile-time symbols come from API 24 class-only stubs; runtime behavior still depends on the device OS version.
- AutoJs6 JVM Entry API 2: `JvmScriptContext` is the only supported host bridge.
- Kotlin standard library 2.3.21 (pinned to the embedded compiler version).
- `kotlinx-coroutines-core-jvm` 1.11.0: `runBlocking`, structured `async`, `delay`, and `Dispatchers.Default` / `IO` / `Unconfined` are supported.

#### Unavailable

- `kotlinx-coroutines-android` and `Dispatchers.Main`: the worker process has no UI/Looper, so dispatching to Main fails.
- Full `kotlin-reflect`: only basic stdlib class references remain; `kotlin.reflect.full.*` is unsupported.
- kotlinx-serialization, coroutine debug/test modules, compiler plugins, and any transitive Maven dependencies.

******

### Security & Isolation

******

The plugin is designed deny-by-default; the following restrictions are always in effect:

- The compiler and worker run in separate processes and never enter the AutoJs6 process; services accept same-signature host calls only.
- Every host capability (app launch, toast, etc.) is authorized per request; unauthorized capabilities are rejected before dispatch.
- Source, artifacts, and diagnostics all have size ceilings; diagnostics truncate only at Unicode code-point boundaries — never half an emoji or malformed UTF-8.
- Outward error messages carry only stable error codes and sanitized text, never private paths, digests, or process identities.
- The compilation cache is authenticated; any toolchain or runtime-library change automatically invalidates all previous caches.

******

### Release History

******

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

##### For more release history, see

* [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-en.md)

******

### Build

******

The repository ships frozen protocol AARs (`protocol/`) and builds offline without an AutoJs6 checkout. JDK 21 is recommended; the Android SDK must provide platforms 24 and 36. Debug build:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Build parameters are centralized in `version.properties`: current version 0.7.1-m10 (build 23), minSdk 26, targetSdk 36.

Release/debug APKs must be signed with the same certificate as AutoJs6 to be accepted by the host; local signing material lives in the version-control-ignored `sign.properties` and `app/sm003.jks`. See [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md) for the full gate commands and release flow.

******

### Resource Layout

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` localizes the plugin name and description; README and CHANGELOG are generated by `.python/generate_markdown.py` from the JSON sources. To change the docs, edit the JSON sources rather than the generated Markdown.

******

### Links

******

- AutoJs6 documentation: https://docs.autojs6.com
- AutoJs6 project home: https://github.com/SuperMonster003/AutoJs6
- Sister plugin Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Kotlin official project: https://github.com/JetBrains/kotlin
- Samples directory: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- Project roadmap (with per-milestone verification records): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- Third-party notices: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
