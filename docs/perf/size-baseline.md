# APK size baseline and M7 budget

Date: 2026-08-25 (Asia/Shanghai)

All sizes below come from host-aligned local builds. Gradle ran offline, and `apkanalyzer` came from
Android SDK command-line tools. Decimal MB is used for the Roadmap budget; MiB is included to avoid
unit ambiguity.

## Result

| Build | M6 baseline | M7 release | Change |
|---|---:|---:|---:|
| debug | 54.1 MB (recorded baseline) | 38,437,253 B / 38.44 MB / 36.66 MiB | about -15.66 MB |
| release | 45,339,694 B / 45.34 MB / 43.24 MiB | 30,383,822 B / 30.38 MB / 28.98 MiB | -14,955,872 B / -32.99% |

The M7 release budget is **at most 40 MB decimal**. The release is 9.62 MB below that limit.

## What changed

The APK previously copied the complete API 24 `android.jar` into
`assets/compiler-classpath/android.jar`. That input was 34,266,947 bytes before APK compression and
included framework resources that kotlinc never reads. Its M6 APK contribution was approximately
17.70 MB compressed.

`prepareJvmSourceCompilerClasspath` now creates a deterministic class-only compiler stub:

- 3,823 sorted `.class` entries and zero non-class entries;
- ZIP timestamps fixed at epoch zero;
- build-time minimum-entry and required-class checks for `android.app.Activity`,
  `android.os.Build`, and `java.lang.Object`;
- generated JAR size 3,300,306 bytes; `apkanalyzer` estimated a 2,739,338-byte APK download
  contribution.

The final release passed the complete offline unit/Lint/assembly gate and the real-device Kotlin →
D8 → worker benchmark, stress, and fault suite, so the smaller stub is sufficient for the current
controlled source profile.

## `apkanalyzer` top ten

Command:

```powershell
$apkanalyzer = "$env:ANDROID_HOME\cmdline-tools\latest\bin\apkanalyzer.bat"
& $apkanalyzer files list --download-size --files-only app/build/outputs/apk/release/app-release.apk
```

The table is sorted by `apkanalyzer` estimated download bytes. Percentages use the 30,383,822-byte
APK as the denominator.

| Rank | APK entry | Estimated bytes | APK share |
|---:|---|---:|---:|
| 1 | `classes6.dex` | 4,070,805 | 13.40% |
| 2 | `classes5.dex` | 3,790,473 | 12.48% |
| 3 | `classes4.dex` | 3,558,628 | 11.71% |
| 4 | `classes.dex` | 3,310,078 | 10.89% |
| 5 | `resources/new_api_database.ser` | 3,036,339 | 9.99% |
| 6 | `classes7.dex` | 2,865,824 | 9.43% |
| 7 | `assets/compiler-classpath/android.jar` | 2,739,338 | 9.02% |
| 8 | `classes3.dex` | 2,678,230 | 8.81% |
| 9 | `classes8.dex` | 2,234,836 | 7.36% |
| 10 | `assets/compiler-classpath/kotlin-stdlib.jar` | 1,683,596 | 5.54% |

The multiple DEX files and `new_api_database.ser` are part of the embedded Kotlin compiler graph;
the original assumption that compiler DEX would dominate was correct. The class-only Android stub
removes the largest avoidable non-DEX contribution.

## R8 and resource shrinking decision

M7 temporarily enabled both `isMinifyEnabled` and `isShrinkResources`, then ran R8 compat mode:

```powershell
.\gradlew.bat :app:assembleRelease --offline `
  '--project-prop=android.enableR8.fullMode=false' --rerun-tasks
```

`minifyReleaseWithR8` failed before packaging. AGP generated 144 `-dontwarn` suggestions spanning
desktop/JDK-only surfaces such as `com.sun.tools.javac.*`, `java.lang.management.*`,
`javax.lang.model.*`, `javax.script.*`, `javax.tools.*`, and `javax.xml.stream.*`, plus optional
compiler-plugin annotations. These types are intentionally absent from the Android runtime and the
compiler uses reflection, service loading, and bytecode-patched compatibility boundaries. Adding a
broad suppression set would make shrinker correctness unverifiable and could hide a real compiler
upgrade incompatibility.

Resource shrinking also requires code minification, so it cannot be enabled independently for this
application. The reviewed decision is therefore:

- keep `isMinifyEnabled = false` and `isShrinkResources = false`;
- reject broad `-dontwarn`/keep expansion;
- retain the deterministic class-only `android.jar`, which meets the size budget without weakening
  runtime verification.
