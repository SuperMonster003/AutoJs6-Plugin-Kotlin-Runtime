# JVM Source Protocol 1.2 capability proposal

Status: **host review requested; no Protocol 1.2 implementation is shipped by this repository**  
Prepared: 2026-08-26 (Asia/Shanghai)  
Target repositories: AutoJs6 host and the independent Java/Kotlin JVM source providers

## 1. Decision requested

Protocol 1.1 already provides a bounded host bridge for `app.launch` and `toast.show`, plus local
`console.stream` and `runtime.sleep`. This proposal asks the host project to accept the security and
wire-shape direction for five candidate capability families, in this order:

1. `clipboard.read` / `clipboard.write`;
2. `storage.read` / `storage.write` for user-selected documents;
3. `http.request` through a host-owned HTTPS proxy;
4. `notification.post` through a host-owned channel;
5. `engines.exec`, deferred until re-entrancy and delegated-capability rules are solved.

The first implementation should contain only capabilities whose host policy, payload codec,
Entry API, provider proxy, and conformance tests land together. Merely adding enum constants is not
an implementation.

## 2. Compatibility decision

### 2.1 Version shape

- Keep the existing AIDL methods and file-descriptor ownership unchanged.
- Advance the tagged wire schema and JVM Source Protocol minor version from 1.1 to 1.2.
- Advance the script-facing Entry API from 2 to 3 when the first new context method is exposed.
- A compatibility provider may advertise `protocolMin=1.1` and `protocolMax=1.2`; a provider that
  requires a new capability advertises `1.2..1.2`.
- A 1.2 host continues to accept a 1.1-only provider and negotiates 1.1. A 1.1 host accepts a
  `1.1..1.2` provider and negotiates 1.1, but rejects a `1.2..1.2` provider.
- The selected protocol is always the highest version in the host/provider range intersection.

Entry API 3 must not be required merely because a binary was built with the newer AAR. It becomes
required only when a request grants an Entry API 3 capability or source compilation uses the new
surface. This needs an explicit host/provider decision because Protocol 1.1 currently validates
`entryApiVersion` as an exact value.

### 2.2 Forward-readable capability fields

The current handshake retrieves provider info and capabilities before it can send a negotiated
request. Therefore a 1.2 provider must not place unknown values into the existing required enum
list when talking to a 1.1 reader. Otherwise the old host can fail while decoding capabilities and
never reach negotiation.

The recommended 1.2 encoding is:

- retain the Protocol 1.1 enum fields as the decodable baseline;
- add optional tagged string fields such as `extensionCapabilities` and
  `grantedExtensionCapabilities`;
- require reverse-DNS-free canonical names matching the existing host-method grammar;
- let a 1.1 reader ignore those optional fields;
- let a 1.2 host include extension grants only after negotiating 1.2;
- reject unknown fields marked `requiredForReader` exactly as Protocol 1.1 already does.

This avoids an AIDL change and preserves the current unknown-optional-field rule. A later redesign
that asks the provider for version-specific capabilities would alter the Binder method surface and
belongs to a major protocol revision.

## 3. Common authorization envelope

Provider capability advertisement means “implemented,” not “authorized.” For every session, the
host calculates the effective grant as the intersection of:

1. capabilities supported by the provider;
2. capabilities supported by the negotiated protocol and Entry API;
3. persistent host policy for the exact provider component and signer;
4. resource-specific user grants, such as an origin or selected document;
5. optional one-run approval for sensitive operations.

The immutable result is copied into the request and its host-call allowlist. Every grant and opaque
resource handle is bound to the request ID, pinned provider identity, caller UID/PID chain, and
session lifetime. No capability is inherited by a nested execution.

Every worker-side method must use this order:

1. **authorization check** — cancellation, capability grant, allowed host method, and scoped token;
2. **payload validation** — canonical JSON, duplicate-key rejection, UTF-8 and method-specific
   bounds;
3. **dispatch** — monotonic call ID, exact request ID, fixed compiler proxy identity, and timeout;
4. **response validation** — response identity, bounded canonical payload, expected shape, and
   stable public error.

The host repeats the same checks before accessing platform APIs. A validation or authorization
failure must stop before the platform operation. The provider must never include a private path,
content URI, credential, exception, response body, or platform implementation detail in a public
error.

Protocol 1.1's 64 KiB encoded host-call and response ceiling remains an absolute limit. Individual
capabilities use lower limits below. Calls remain cancellable and subject to the request deadline.

Recommended stable response errors:

| Error | Meaning |
|---|---|
| `CAPABILITY_NOT_GRANTED` | The immutable request grant or method allowlist does not permit the call. |
| `INVALID_PAYLOAD` | Canonical JSON, field, encoding, or semantic validation failed. |
| `PAYLOAD_TOO_LARGE` | A method-specific or protocol-wide byte limit was exceeded. |
| `USER_INTERACTION_REQUIRED` | A foreground picker or explicit approval is required. |
| `PLATFORM_DENIED` | Android permission or current platform state denies the operation. |
| `SCOPE_VIOLATION` | A document, origin, channel, or engine is outside the grant. |
| `RATE_LIMITED` | The per-session operation budget was exhausted. |
| `TIMEOUT` | The host operation exceeded its negotiated timeout. |
| `RESPONSE_TOO_LARGE` | A bounded result could not fit without truncating semantic data. |
| `OPERATION_FAILED` | A sanitized platform operation failed for a non-policy reason. |
| `BRIDGE_CLOSED` | Cancellation, close, host death, or retirement closed the bridge. |

## 4. Capability contracts

### 4.1 Clipboard — priority P0

| Property | Proposed contract |
|---|---|
| Capability names | `clipboard.read`, `clipboard.write` |
| Entry API | `context.clipboard().readText()` and `writeText(text, sensitive=true)` |
| Data types | Plain UTF-8 text only; no URI, Intent, HTML, styled span, or coercion |
| Text limit | 32 KiB UTF-8 per read or write |
| Session budget | At most 4 reads and 16 writes |
| Grant | Separate read/write grant; read defaults to one run and foreground-only |
| Result | Read returns nullable text; write returns canonical boolean |

The host performs clipboard access, never the worker. Reads require an interactive foreground host
state and must not support polling. Writes default to the Android sensitive-content flag unless a
host UI explicitly grants a non-sensitive write. The host may return `PLATFORM_DENIED` when Android
background clipboard rules prevent access.

Android restricts clipboard access from Android 10 onward, surfaces reads to the user on newer
versions, and supports `ClipDescription.EXTRA_IS_SENSITIVE`; those platform behaviors are part of
the host policy rather than something the plugin may bypass. See Android's
[secure clipboard handling guidance](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling).

### 4.2 Scoped storage — priority P1

| Property | Proposed contract |
|---|---|
| Capability names | `storage.read`, `storage.write` |
| Entry API | `context.storage().read(grantId)` and `write(grantId, bytes, mode)` |
| Resource identity | Host-issued opaque `grantId`; raw paths and content URIs are forbidden |
| Data limit | 44 KiB decoded bytes per call; no silent truncation |
| Session budget | At most 64 reads and 32 writes |
| Grant | User-selected document, with read and write scopes stored separately by the host |
| Write modes | `replace` or `append`; no arbitrary seek in the first revision |

The initial 1.2 shape intentionally supports only one bounded chunk per call. Base64 expansion plus
JSON metadata must remain below 64 KiB. Larger files need a separately reviewed streaming/FD
design; they must not be simulated with unbounded Binder metadata or implicit temporary paths.

The host obtains document access through Android's system picker and stores any persistable URI
permission. Only an opaque grant ID crosses the JVM source bridge. Each call revalidates that the
grant still exists, has the requested read/write bit, refers to the same document, and remains
within the request scope. The worker cannot enumerate storage roots or convert a grant into a raw
filesystem path. See Android's
[Storage Access Framework documentation](https://developer.android.com/guide/topics/providers/document-provider)
and [shared-document access guidance](https://developer.android.com/training/data-storage/shared/documents-files).

### 4.3 Host-proxied network — priority P2

| Property | Proposed contract |
|---|---|
| Capability name / method | `http.request` |
| Entry API | `context.http().request(request)` |
| Schemes | HTTPS only in 1.2 |
| Methods | `GET`, `HEAD`, and `POST` initially |
| Request body | At most 32 KiB decoded bytes |
| Response body | At most 44 KiB decoded bytes; fail instead of truncating |
| Headers | At most 32 entries and 8 KiB total; fixed denylist for credentials/hop-by-hop fields |
| Timeout | At most 15 seconds and never beyond the session deadline |
| Session budget | At most 16 requests; at most 2 in flight |
| Grant | Exact normalized HTTPS origins, with explicit port and optional path prefix |

The host owns DNS, TLS, redirects, decompression and sockets. Every initial URL and redirect is
revalidated against the grant. IP literals and loopback, link-local, private, multicast and other
non-public resolved addresses are denied by default, including after DNS resolution. Redirects are
limited to three. Cookies, ambient authentication, userinfo, proxy overrides, custom trust stores,
client certificates and raw sockets are not exposed. Response size is measured after
decompression.

The host must continue to disallow cleartext traffic and use its normal platform trust policy.
Android documents declarative cleartext and trust-anchor controls in its
[Network Security Configuration guidance](https://developer.android.com/privacy-and-security/security-config).

### 4.4 Notifications — priority P3

| Property | Proposed contract |
|---|---|
| Capability name / method | `notification.post` |
| Entry API | `context.notifications().post(title, text)` |
| Text limits | 256 title code points and 4 KiB UTF-8 body |
| Session budget | At most 8 posts |
| Grant | Explicit per-provider grant plus an enabled host-managed channel |
| Result | Host-owned notification ID, scoped only for replacement during the same session |

The host selects the icon, channel, category, priority and immutable interaction behavior. Scripts
cannot supply `PendingIntent`, full-screen intent, custom `RemoteViews`, URI, arbitrary sound,
channel ID or notification ID. The operation fails closed when the channel is disabled or Android
permission is absent. Android 13 and newer require the host to account for the
[`POST_NOTIFICATIONS` runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

### 4.5 Engine execution — deferred P4

`engines.exec` should not ship in the first Protocol 1.2 implementation. It creates nested
lifecycle, recursion, output ownership, cancellation, cache and confused-deputy questions that the
current single-session provider contract does not answer.

Before admission, a separate design must require host-owned script IDs instead of raw paths,
define a maximum nesting depth (recommended: one), deny capability inheritance, bind child
execution to the parent's cancellation/deadline, and prove that a provider cannot call back into
itself while its single-session gate is held. If any AIDL or descriptor ownership changes are
needed, this capability moves to Protocol 2 rather than 1.2.

## 5. Required implementation slices

For each admitted capability, one host/provider change set must include:

- optional tagged capability/grant fields and malformed/unknown-field codec tests;
- Entry API 3 method and Java compatibility tests;
- host grant persistence, revocation, UI and exact-component/signer binding;
- host bridge policy and method-specific canonical payload codec;
- Kotlin and Java provider proxies using the four-stage call template;
- positive, ungranted, malformed, oversize, timeout, cancellation and response-tampering tests;
- same-signer device conformance through the real Binder path;
- documentation, samples, bilingual user errors and frozen AAR refresh.

No capability is advertised until every required slice is present. A host may implement policy and
codec support first while continuing to negotiate Protocol 1.1 with released providers.

## 6. Acceptance matrix

The host proposal can be accepted independently of implementation when reviewers agree on:

- the optional-string extension encoding or a demonstrably safer alternative;
- Entry API 3 negotiation instead of unconditional exact-version rejection;
- per-capability user-grant and revocation ownership;
- the payload, response, rate and timeout ceilings;
- HTTPS/SSRF controls for `http.request`;
- deferral conditions for `engines.exec`;
- a conformance plan covering old host/new provider and new host/old provider.

Until those decisions land in the AutoJs6 host, this Kotlin provider continues to advertise and
execute only Protocol 1.1 / Entry API 2.

