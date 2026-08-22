# Frozen AutoJs6 protocol inputs

This independent plugin consumes three repository-local AAR snapshots:

- `common-plugin-api.aar` for `org.autojs.plugin.INFO` metadata;
- `protocol-wire-api.aar` for the bounded binary wire format;
- `jvm-source-api.aar` for the JVM source Binder protocol and entry ABI.

`protocol-artifacts.lock.json` records the source worktree revision and the exact SHA-256 of every
artifact. The Gradle `verifyPinnedInputs` task checks the complete file/module set, rejects symlinked
artifacts, and verifies every digest before compilation or assembly.

The current M5 snapshot intentionally records `sourceDirty=true`: Protocol 1.1 and Entry API 2 are
still under integration in the host worktree. This is an honest worktree snapshot, not a claim that
the AARs equal the recorded Git commit. Refresh all three AARs and their lock together whenever the
host protocol source changes.
