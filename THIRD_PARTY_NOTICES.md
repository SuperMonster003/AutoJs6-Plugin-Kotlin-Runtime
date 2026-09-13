# Third-party notices

- Kotlin compiler embeddable and Kotlin standard library 2.3.21 — Apache License 2.0.
- JetBrains Trove4J 1.0.20200330 — GNU Lesser General Public License 2.1.
- Android D8/R8 8.13.17 — BSD-style Android Open Source Project license.
- desugar_jdk_libs_nio 2.1.5 — Android Open Source Project licenses.

The corresponding artifacts are resolved at build time from pinned dependency coordinates; this
repository does not modify their source code.


The upstream compiler source is not vendored or edited here. The build does transform the compiler artifact with shape-checked Android compatibility bytecode patches. See `app/build.gradle.kts` (compiler patch tasks) and `docs/RELEASE_CHECKLIST.md` for the exact pinned compiler, patch validation and release checks. Preserve upstream LICENSE/NOTICE resources from the compiler and its dependencies, including Trove4j, when redistributing the APK or matching sources.
