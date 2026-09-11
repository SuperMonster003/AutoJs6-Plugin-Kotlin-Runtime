<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="{{ repo_url }}/blob/{{ default_branch }}/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>{{ text_plugin_synopsis }}</p>

  <p>
    <a href="{{ repo_url }}/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="{{ repo_url }}/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="{{ repo_url }}/commit/{{ created_commit }}"><img alt="Created" src="https://img.shields.io/date/{{ created_timestamp }}?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="{{ repo_url }}/blob/{{ default_branch }}/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### {{ h3_languages_with_ascii }}

******

{{ p_languages_all_supported_for_readme }}:

{{ placeholder_ul_languages_all_supported }}

******

### {{ h3_introduction }}

******

{{ p_introduction }}

{{ p_introduction_extra }}

******

### {{ h3_functions }}

******

{{ placeholder_features }}

******

### {{ h3_quick_start }}

******

- **{{ quick_start_install_title }}** — {{ quick_start_install }}
- **{{ quick_start_enable_title }}** — {{ quick_start_enable }}
- **{{ quick_start_run_title }}** — {{ quick_start_run }}
- **{{ quick_start_debug_title }}** — {{ quick_start_debug }}

******

### {{ h3_usage }}

******

{{ p_usage_intro }}:

```kotlin
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("Hello from Kotlin {{ kotlin_version }}")
        context.toast("AutoJs6 Kotlin Runtime")
        context.sleep(500L)
        val launched = context.app().launch("org.autojs.autojs6")
        return launched
    }
}
```

{{ p_usage_note }}

******

### {{ h3_boundaries }}

******

{{ p_boundaries_intro }}:

{{ placeholder_boundaries }}

******

### {{ h3_runtime_profile }}

******

{{ p_runtime_profile_intro }}:

#### {{ h4_runtime_available }}

{{ placeholder_runtime_available }}

#### {{ h4_runtime_unavailable }}

{{ placeholder_runtime_unavailable }}

******

### {{ h3_security }}

******

{{ p_security_intro }}:

{{ placeholder_security_limits }}

******

### {{ h3_release_history }}

******

{{ placeholder_latest_release_history }}

##### {{ h5_for_more_release_history }}

* {{ placeholder_read_more_in_changelog_md }}

******

### {{ h3_build }}

******

{{ p_build_intro }}:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

{{ text_release_build }}:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

{{ p_build_params }}.

{{ p_build_signing }}.

******

### {{ h3_resource_layout }}

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

{{ p_resource_layout }}.

******

### {{ h3_links }}

******

- {{ text_link_autojs6_docs }}: {{ docs_autojs6_url }}
- {{ text_link_autojs6_repo }}: {{ autojs6_repo_url }}
- {{ text_link_java_runtime }}: {{ java_runtime_repo_url }}
- {{ text_link_kotlin_official }}: {{ kotlin_official_url }}
- {{ text_link_samples }}: {{ samples_url }}
- {{ text_link_roadmap }}: {{ roadmap_url }}
- {{ text_link_third_party }}: {{ third_party_url }}


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
