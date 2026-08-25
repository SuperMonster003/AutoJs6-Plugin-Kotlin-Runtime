# Kotlin source layout and entry naming

Status: Accepted for M8 on 2026-08-25

## Context

Protocol 1.1 carries both `sourceFileName` and a fully qualified `entryClassName`, so the wire shape
does not intrinsically require the simple name `Main`. The current AutoJs6 host implementation was
inspected at revision `c59203a595c063bf4fa7498b091e91680096b7f4`: its
`JvmSourceR1Policy.ENTRY_SIMPLE_NAME` is `Main`, it normalizes every Kotlin document to `Main.kt`,
and it derives only `<optional ASCII package>.Main`. The provider independently validates the two
request fields and must not assume that every future compliant caller will make the same choice.

The host and provider package scanners both accept ordinary dotted ASCII identifiers. Supporting a
Kotlin declaration such as ``package `escaped-name` `` or `package 中文.脚本` in only the provider
would be incomplete: host layout derivation would disagree, and the provider's class/JAR/DEX name
validators currently admit the same conservative ASCII profile. Silently treating such syntax as
the default package is worse than rejecting it because the resulting error appears to be a missing
entry rather than an input-shape decision.

## Decision

1. Keep the Protocol 1.1 package profile at zero or one dotted package declaration made of
   ordinary ASCII identifiers (`[A-Za-z_][A-Za-z0-9_]*`). Backtick-escaped and non-ASCII package
   identifiers remain unsupported in M8.
2. Reject an unsupported package declaration at `INVALID_REQUEST / INPUT` with the stable public
   message: `Kotlin package declarations support only ordinary ASCII identifiers; escaped or
   non-ASCII identifiers are not supported`.
3. Reject a valid package that disagrees with the requested fully qualified entry with the stable
   public message: `Kotlin package does not match the requested entry class`.
4. Retain `Main` only as the default used by host-compatible helpers. The provider continues to
   derive its expected source file from the actual request and accepts an alternative ordinary
   ASCII simple name when `sourceFileName`, `entryClassName`, source declaration, compiled class,
   and worker load request all agree.
5. Treat multiple top-level classes and nested classes as valid output shapes as long as the exact
   requested class is the only concrete `AutoJsJvmEntry`. A nested `Helper$Main` is distinct from
   top-level `Main`. Kotlin `object`, interface, abstract class, and a class without a public
   no-argument constructor are not valid entry shapes.

Only predefined source-policy messages are allowed to cross the provider boundary. Private paths,
digests, process identities, arbitrary exception messages, and compiler internals retain the
existing redaction/fallback behavior.

## Executable evidence

- `KotlinSourcePolicyTest` covers alternative protocol-requested names, ASCII packages, backticks,
  Unicode identifiers, duplicate packages, and package/request mismatch.
- `EntryClassAnalyzerTest` covers multiple top-level classes, a nested class also named `Main`,
  missing and ambiguous entries, and object/interface/abstract/constructor-incompatible shapes
  with exact diagnostic text.
- `KotlinErrorSamplesTest` consumes the checked-in fixtures under `samples/errors/` and verifies
  their input, compiler, or entry-analysis failure stage.
- The same-signer Android harness method `m8SourceShapeAndDiagnosticContract` sends an alternative
  `ScriptEntry.kt` / `ScriptEntry` request through the real Binder provider and verifies the public
  package and entry messages independently of the current host's fixed layout generator.

## Revisit condition

Unicode or escaped package names require a coordinated host/protocol profile change, not a local
regular-expression relaxation. Revisit only when the host layout parser, protocol validation,
class/JAR/DEX validators, cache canonicalization, and device tests can adopt the same identifier
grammar in one compatibility change.
