# AutoJs6 Gradle Platform Versions source snapshot

This included build is an unmodified source snapshot of
`AutoJs6-Gradle-Platform-Versions` 1.4.1 at commit
`dcf5d9a6b0de56fef34fcf6929478d86b1693fd0`.

It is kept in this repository because version 1.4.1 was available only from the maintainer's local
Maven repository when the M6 build migration was finalized. Keeping the source snapshot here makes
clean CI jobs and `git archive` checkouts reproducible while preserving the same plugin ID and
version-selection behavior:

```text
org.autojs.build.platform-versions:1.4.1
```

When an immutable public artifact becomes available, replace this included build with that artifact,
verify the selected AGP/Kotlin/R8 versions, and remove this directory in the same change.

The snapshot is licensed under the Mozilla Public License 2.0; see `LICENSE` in this directory.
