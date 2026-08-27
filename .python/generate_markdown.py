# -*- coding: utf-8 -*-
"""Generate localized README/CHANGELOG markdown from the JSON sources.

Source of truth:
    .readme/common.json          -- language-neutral facts (versions, ids, urls)
    .readme/lang_<code>.json     -- localized README strings
    .readme/template_readme.md   -- README skeleton with {{ placeholders }}
    .changelog/lang_<code>.json  -- localized changelog labels and per-version data
    .changelog/template_changelog.md

Outputs:
    .readme/README-<code>.md     -- one README per language
    README.md                    -- repository root, default language copy
    .changelog/CHANGELOG-<code>.md
    CHANGELOG.md                 -- repository root, default language copy

Edit the JSON sources, never the generated markdown.
"""
import json
import re
from pathlib import Path


LANGUAGE_CODES = [
    "zh-Hans",
    "zh-Hant-HK",
    "zh-Hant-TW",
    "en",
    "fr",
    "es",
    "ja",
    "ko",
    "ru",
    "ar",
]
LANGUAGE_CODE_DEFAULT = "zh-Hans"


ROOT = Path(__file__).resolve().parents[1]
README_DIR = ROOT / ".readme"
CHANGELOG_DIR = ROOT / ".changelog"
TEMPLATE_PATTERN = re.compile(r"\{\{\s*([A-Za-z0-9_$.-]+)\s*\}\}")


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def render_template(text: str, values: dict) -> str:
    def replace(match):
        key = match.group(1).strip()
        if key not in values:
            raise KeyError(f"Missing template value: {key}")
        return str(values[key])

    return TEMPLATE_PATTERN.sub(replace, text)


def render_dynamic(value, values: dict):
    if isinstance(value, dict):
        return {key: render_dynamic(item, values) for key, item in value.items()}
    if isinstance(value, list):
        return [render_dynamic(item, values) for item in value]
    if isinstance(value, str):
        return render_template(value, values)
    return value


def validate_language_keys(raw_languages: dict):
    expected = list(raw_languages[LANGUAGE_CODE_DEFAULT].keys())
    for code in LANGUAGE_CODES:
        actual = list(raw_languages[code].keys())
        if actual != expected:
            raise ValueError(
                f"README locale key/order mismatch for {code}: "
                f"expected {expected}, actual {actual}"
            )


def validate_changelog_keys(raw_changelogs: dict):
    expected_labels = [
        "changelog_label_hint",
        "changelog_label_feature",
        "changelog_label_fix",
        "changelog_label_improvement",
        "changelog_label_dependency",
        "$data",
    ]
    expected_versions = list(raw_changelogs[LANGUAGE_CODE_DEFAULT]["$data"].keys())
    for code in LANGUAGE_CODES:
        actual_labels = list(raw_changelogs[code].keys())
        if actual_labels != expected_labels:
            raise ValueError(
                f"Changelog locale key/order mismatch for {code}: "
                f"expected {expected_labels}, actual {actual_labels}"
            )
        actual_versions = list(raw_changelogs[code]["$data"].keys())
        if actual_versions != expected_versions:
            raise ValueError(
                f"Changelog version/order mismatch for {code}: "
                f"expected {expected_versions}, actual {actual_versions}"
            )


def bullet_list(items):
    return "\n".join(f"- {item}" for item in items)


def markdown_link(label, url):
    return f"[{label}]({url})"


def load_languages():
    common = load_json(README_DIR / "common.json")
    raw_languages = {
        code: load_json(README_DIR / f"lang_{code}.json") for code in LANGUAGE_CODES
    }
    raw_changelogs = {
        code: load_json(CHANGELOG_DIR / f"lang_{code}.json") for code in LANGUAGE_CODES
    }
    validate_language_keys(raw_languages)
    validate_changelog_keys(raw_changelogs)

    languages = {}
    changelogs = {}
    for code in LANGUAGE_CODES:
        merged_language = {**common, **raw_languages[code]}
        languages[code] = render_dynamic(merged_language, merged_language)

        changelog_values = {
            key: value for key, value in raw_changelogs[code].items() if key != "$data"
        }
        changelog_values = render_dynamic(changelog_values, changelog_values)
        changelogs[code] = {
            "values": changelog_values,
            "data": render_dynamic(
                raw_changelogs[code]["$data"],
                {**common, **changelog_values},
            ),
        }
    return languages, changelogs


def format_changelog_items(changelog, limit=None):
    values = changelog["values"]
    chunks = []
    for index, (version_name, item) in enumerate(changelog["data"].items()):
        if limit is not None and index >= limit:
            break
        lines = [
            f"# {version_name}",
            "",
            f"###### {item['released_date']}",
            "",
        ]
        for category in ["hint", "feature", "fix", "improvement", "dependency"]:
            for item_text in item.get(category, []):
                lines.append(f"* `{values[f'changelog_label_{category}']}` {item_text}")
        chunks.append("\n".join(lines).rstrip())
    return "\n\n".join(chunks).rstrip() + "\n"


def build_language_list(target_code, languages):
    repo_url = languages[target_code]["repo_url"]
    default_branch = languages[target_code]["default_branch"]
    lines = []
    for code in LANGUAGE_CODES:
        content = languages[code]
        label = f"{content['$name']} [{code}]"
        if code == target_code:
            lines.append(f"- {label} # {content['text_current_lowercase']}")
        else:
            lines.append(
                f"- {markdown_link(label, f'{repo_url}/blob/{default_branch}/.readme/README-{code}.md')}"
            )
    return "\n".join(lines)


def build_readme_values(code, languages, changelogs):
    content = dict(languages[code])
    repo_url = content["repo_url"]
    default_branch = content["default_branch"]
    content["placeholder_ul_languages_all_supported"] = build_language_list(code, languages)
    content["placeholder_features"] = bullet_list(content["features"])
    content["placeholder_boundaries"] = bullet_list(content["boundaries"])
    content["placeholder_runtime_available"] = bullet_list(content["runtime_available"])
    content["placeholder_runtime_unavailable"] = bullet_list(content["runtime_unavailable"])
    content["placeholder_security_limits"] = bullet_list(content["security_limits"])
    content["placeholder_latest_release_history"] = format_changelog_items(
        changelogs[code], limit=3
    ).rstrip()
    content["placeholder_read_more_in_changelog_md"] = markdown_link(
        f"CHANGELOG-{code}.md",
        f"{repo_url}/blob/{default_branch}/.changelog/CHANGELOG-{code}.md",
    )
    return content


def write_text(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")
    print(f"Generated {path.relative_to(ROOT)}")


def generate_readmes(languages, changelogs):
    template = (README_DIR / "template_readme.md").read_text(encoding="utf-8")
    for code in LANGUAGE_CODES:
        output = render_template(template, build_readme_values(code, languages, changelogs))
        if TEMPLATE_PATTERN.search(output):
            raise ValueError(f"Unresolved README placeholder for {code}")
        write_text(README_DIR / f"README-{code}.md", output)
        if code == LANGUAGE_CODE_DEFAULT:
            write_text(ROOT / "README.md", output)


def generate_changelogs(languages, changelogs):
    template = (CHANGELOG_DIR / "template_changelog.md").read_text(encoding="utf-8")
    for code in LANGUAGE_CODES:
        values = dict(languages[code])
        values["placeholder_release_history"] = format_changelog_items(
            changelogs[code]
        ).rstrip()
        output = render_template(template, values)
        if TEMPLATE_PATTERN.search(output):
            raise ValueError(f"Unresolved changelog placeholder for {code}")
        write_text(CHANGELOG_DIR / f"CHANGELOG-{code}.md", output)
        if code == LANGUAGE_CODE_DEFAULT:
            write_text(ROOT / "CHANGELOG.md", output)


def main():
    if LANGUAGE_CODE_DEFAULT not in LANGUAGE_CODES:
        raise ValueError(f"Default language code {LANGUAGE_CODE_DEFAULT!r} is not supported")
    languages, changelogs = load_languages()
    generate_changelogs(languages, changelogs)
    generate_readmes(languages, changelogs)


if __name__ == "__main__":
    main()
