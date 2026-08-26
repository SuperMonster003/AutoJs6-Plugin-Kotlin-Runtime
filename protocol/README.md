# Frozen AutoJs6 protocol inputs

This independent plugin consumes three repository-local AAR snapshots:

- `common-plugin-api.aar` for `org.autojs.plugin.INFO` metadata;
- `protocol-wire-api.aar` for the bounded binary wire format;
- `jvm-source-api.aar` for the JVM source Binder protocol and entry ABI.

`protocol-artifacts.lock.json` records the clean host source revision, `debug` build variant, exact
Gradle task/output, and SHA-256 of every artifact. The schema-2 Gradle `verifyPinnedInputs` task
checks the complete metadata/file/module set, rejects dirty provenance and symlinked artifacts, and
verifies every digest before compilation or assembly.

The current snapshot records `sourceDirty=false` and points to the host commit that finalized
Protocol 1.1 and Entry API 2. Refresh all three AARs and their lock together whenever the host
protocol source changes. Use the staging-first procedure in [`docs/PROTOCOL_REFRESH.md`](../docs/PROTOCOL_REFRESH.md);
generated AARs never overwrite this directory before semantic review.
