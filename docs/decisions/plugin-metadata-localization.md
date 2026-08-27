# Plugin metadata localization

Status: Accepted for the M11 documentation line on 2026-08-27

## Host display contract

AutoJs6 host commit `46e5caaa99de0dc62bdbf512da2795c142373aa3` introduced the current
Plugin Center resource resolver. Installed-plugin discovery binds the signature-protected
`org.autojs.plugin.INFO` service, then resolves `plugin_description` from the plugin APK with a
configuration context whose locale is `Language.getPrefLanguage().locale`. The host language enum
contains the same ten language tags used by this repository's README pipeline:

| Documentation tag | Android resource directory |
| --- | --- |
| `zh-Hans` | `values-zh` |
| `zh-Hant-HK` | `values-zh-rHK` |
| `zh-Hant-TW` | `values-zh-rTW` |
| `en` | `values-en` |
| `fr` | `values-fr` |
| `es` | `values-es` |
| `ja` | `values-ja` |
| `ko` | `values-ko` |
| `ru` | `values-ru` |
| `ar` | `values-ar` |

The host's own resources and established AutoJs6 plugins use these qualifiers. A generic
`values-zh` resource is preferable to the previous `values-zh-rCN` resource because it covers the
host's `zh-Hans` locale and its compatible plain-`zh` fallback without tying Simplified Chinese to
one region. `values/strings.xml` remains the required English fallback, and `values-en` is retained
as the explicit English member of the ten-language matrix.

## Decision

Localize `app_name`, `plugin_name`, `plugin_description`, and `plugin_instruction` for all ten
host languages. Keep `plugin_author` present and identical so every locale has the same complete
key set. This affects presentation only; provider IDs, protocol fields, capabilities, entry API,
and service discovery remain unchanged.

Use `.python/generate_markdown.py` as the shared locale-contract gate. Its
`ANDROID_STRING_DIRECTORIES` keys must match `LANGUAGE_CODES` in the same order. For the default
file and every localized `strings.xml`, the generator rejects malformed XML, missing or duplicate
names, blank values, and key/order drift. It additionally requires `values/strings.xml` to equal
`values-en/strings.xml`, preventing the English fallback from diverging from explicit English.
The generator validates rather than writes Android XML because these files are compiled product
inputs and should remain reviewable localization sources.

## Compatibility boundary

The frozen PluginInfo API returns strings, so the discovery service continues to use
`getString(...)` exactly as existing AutoJs6 plugins do. On hosts with the current resolver, the
description is explicitly reloaded using the host-selected locale. The application label and
bound service's name/instruction also benefit from Android resource selection, but an older host
or a host language that differs from the device locale may retain its historical fallback
behavior. Changing the wire values to resource-reference tokens would display those tokens
literally on older hosts, so that is not a compatible plugin-only change.

## Verification

- `.python/generate_markdown.py` validates the common ten-language/resource-key matrix before
  generating README and CHANGELOG outputs.
- Android resource processing proves all locale files compile and can be packaged.
- App unit tests and Lint remain the regression gates for the discovery service and resources.
