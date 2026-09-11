<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6용 Kotlin 2.3.21 단일 파일 소스 컴파일/실행 플러그인</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/commit/17f42fa7aa2a2046c74e558f313b7510d155f365"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어 (Languages)

******

현재 README.md는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### 소개

******

AutoJs6 Kotlin Runtime 플러그인을 사용하면 AutoJs6에서 단일 파일 Kotlin 소스 코드 (`.kt`)를 직접 컴파일하고 실행할 수 있습니다. 플러그인은 Kotlin/JVM 2.3.21 컴파일러 (K2)와 D8 8.13.17 바이트코드 변환기를 내장하며, 컴파일 결과물은 일회용 worker 프로세스에서 실행됩니다. 컴파일러와 스크립트 모두 AutoJs6 프로세스 안에서는 절대 실행되지 않습니다.

이 플러그인은 [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime)과 자매 플러그인 관계로, 함께 설치하여 각각 Kotlin / Java 소스를 담당할 수 있습니다. AutoJs6는 언어별로 선택된 컴파일러 구성 요소를 기억합니다.

******

### 기능

******

- `org.autojs.plugin.JVM_SOURCE` 컴파일/실행 서비스와 `org.autojs.plugin.INFO` 플러그인 센터 발견 서비스를 제공하며, 둘 다 서명으로 보호되고 독립된 보조 프로세스에서 동작합니다.
- Kotlin/JVM 2.3.21 컴파일러 (K2)를 내장. 스크립트는 JVM 1.8 바이트코드를 대상으로 하며 D8 8.13.17로 DEX 변환 후 실행됩니다.
- 개별 인가되는 4가지 호스트 기능 브리지 지원: 콘솔 실시간 출력 `console().log/error`, 앱 실행 `app().launch`, 중단 가능한 `sleep`, `toast` 메시지.
- Kotlin 표준 라이브러리와 `kotlinx-coroutines-core-jvm` 1.11.0 구조화 동시성 (`Dispatchers.Default` / `IO` / `Unconfined`) 지원.
- 인증된 컴파일 캐시: 동일 소스의 웜 히트 중앙값 약 46 ms, 콜드 컴파일 약 577 ms (실기기 기준 약 12.5배 가속). 실행 중앙값 약 30 ms.
- 컴파일 오류는 K2 원본 진단과 소스 행/열 위치를 유지합니다. BOM 헤더, Windows 경로 차이, 중국어/이모지 잘림도 결정론적으로 처리됩니다.
- 매 실행은 새로운 일회용 worker 프로세스에서 수행되며 종료 후 폐기됩니다. 호스트에서 스크립트를 중지하면 sleep과 코루틴이 즉시 중단됩니다.
- README와 CHANGELOG는 간체 중국어/번체 중국어 (홍콩/대만)/영어/프랑스어/스페인어/일본어/한국어/러시아어/아랍어 등 10개 언어를 지원합니다.

******

### 빠른 시작

******

- **설치** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases)에서 APK를 내려받아 설치하거나, 아래 「빌드」 절에 따라 로컬 빌드합니다. 주의: 플러그인은 AutoJs6와 동일한 인증서로 서명되어야 합니다. GitHub Actions가 생성하는 임시 인증서 debug APK는 빌드 검사용일 뿐 실기기 통합에는 사용할 수 없습니다. 호스트 AutoJs6 버전 코드는 5276 이상이어야 합니다.
- **활성화** — JVM 소스 실행은 현재 AutoJs6의 실험적 기능입니다: 호스트에서 실험 스위치를 켜고, Kotlin 언어의 컴파일러 구성 요소로 이 플러그인을 명시적으로 선택하세요. 설정이 없으면 실행 시 각각 안정 오류 코드 `JVM_SOURCE_EXPERIMENT_DISABLED` / `JVM_SOURCE_PROVIDER_NOT_SELECTED`가 표시됩니다.
- **실행** — AutoJs6 에디터에서 `.kt` 파일을 새로 만들고, `AutoJsJvmEntry` 인터페이스를 구현한 진입 클래스를 작성한 뒤 실행을 누릅니다 (아래 사용 예시 참조). 현재 호스트는 소스 이름을 `Main.kt`로 정규화하며 진입 단순명은 `Main`으로 고정됩니다. 일반 ASCII 패키지와 import 문은 선택 사항입니다.
- **문제 해결** — 컴파일 실패 시 콘솔에 K2 진단과 행/열 위치가 표시됩니다. 흔한 4가지 오류 (import 누락, 타입 불일치, 진입 인터페이스 미구현, 패키지 불일치)의 이중 언어 해설은 [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors)에 있습니다. 실행 실패는 안정 오류 코드 (`JVM_SOURCE_COMPILE_FAILED`, `JVM_SOURCE_TIMEOUT` 등)만 표시하며 내부 경로를 노출하지 않습니다.

******

### 사용 예시

******

현재 4가지 호스트 기능을 모두 시연하는, 바로 실행 가능한 최소 예제입니다:

```kotlin
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("Hello from Kotlin 2.3.21")
        context.toast("AutoJs6 Kotlin Runtime")
        context.sleep(500L)
        val launched = context.app().launch("org.autojs.autojs6")
        return launched
    }
}
```

`console().log/error`는 스크립트 실행 중 한 줄씩 실시간으로 전달됩니다. `sleep`은 중지 조작으로 즉시 중단할 수 있습니다. 더 많은 예제는 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples) 디렉터리를 참조: 기능 스모크 `m5-capabilities.kt`, 취소 데모 `m6-cancellation.kt`, 코루틴 예제 `coroutines.kt`.

******

### 기능 경계

******

안전하고 예측 가능한 동작을 위해 현재 버전은 의도적으로 다음 경계를 유지합니다:

- 단일 파일 Kotlin 소스만 지원, 상한 4 MiB. 다중 파일 프로젝트, class 파일, JAR, DEX 입력은 아직 미지원.
- 진입 클래스는 `AutoJsJvmEntry` (Entry API 2)를 구현해야 합니다. 패키지 이름은 일반 ASCII 식별자만 지원하며, 백틱 이스케이프나 비 ASCII 패키지는 명시적으로 거부됩니다.
- Maven이나 서드파티 의존성은 해석하지 않습니다. 스크립트에서 쓸 수 있는 라이브러리는 아래 「스크립트 런타임」 목록이 전부입니다.
- 스크립트 바이트코드 대상은 JVM 1.8로 고정. Java 8을 초과하는 class 파일은 D8 이전에 거부됩니다.
- worker 프로세스는 매 실행 후 폐기되므로 코루틴 등 백그라운드 작업은 `run` 반환 후 살아남지 않습니다. `GlobalScope`를 사용하지 마세요.
- 공개 프로토콜은 JVM Source Protocol 1.1입니다. Protocol 1.2는 제안 단계일 뿐이며 클립보드/문서/HTTPS/알림 기능은 아직 개방되지 않았습니다.

******

### 스크립트 런타임

******

스크립트 컴파일과 실행에 쓸 수 있는 라이브러리는 정확히 고정된 허용 목록입니다:

#### 사용 가능

- Android framework: 컴파일 타임 심벌은 API 24 class-only 스텁에서 제공. 런타임 동작은 여전히 기기 OS 버전에 의존.
- AutoJs6 JVM Entry API 2: `JvmScriptContext`가 유일하게 지원되는 호스트 브리지.
- Kotlin 표준 라이브러리 2.3.21 (내장 컴파일러 버전에 고정).
- `kotlinx-coroutines-core-jvm` 1.11.0: `runBlocking`, 구조화 `async`, `delay`, `Dispatchers.Default` / `IO` / `Unconfined` 지원.

#### 사용 불가

- `kotlinx-coroutines-android`와 `Dispatchers.Main`: worker 프로세스에 UI/Looper가 없어 Main 디스패치는 실패합니다.
- 완전한 `kotlin-reflect`: 표준 라이브러리 기본 클래스 참조만 남으며 `kotlin.reflect.full.*`은 미지원.
- kotlinx-serialization, 코루틴 debug/test 모듈, 컴파일러 플러그인, 임의의 Maven 전이 의존성.

******

### 보안과 격리

******

플러그인은 기본 거부 원칙으로 설계되었으며, 다음 제한이 항상 적용됩니다:

- 컴파일러와 worker는 독립 프로세스에서 동작하며 AutoJs6 프로세스에 절대 들어가지 않습니다. 서비스는 동일 서명 호스트 호출만 수락합니다.
- 각 호스트 기능 (앱 실행, toast 등)은 요청별로 인가되며, 인가되지 않은 기능은 디스패치 전에 거부됩니다.
- 소스, 산출물, 진단 모두 크기 상한이 있습니다. 진단은 Unicode 코드 포인트 경계에서만 잘리며, 이모지가 반쪽 나거나 잘못된 UTF-8이 생기지 않습니다.
- 외부 오류 메시지는 안정 오류 코드와 정제된 텍스트만 포함하며, 사설 경로, 다이제스트, 프로세스 정보는 노출되지 않습니다.
- 컴파일 캐시는 인증을 거치며, 툴체인이나 런타임 라이브러리 변경 시 이전 캐시가 전부 자동 무효화됩니다.

******

### 릴리스 이력

******

# v0.7.0

###### 2026/09/11

* `개선` 빌드 시 의도하지 않은 네이티브 의존성을 거부하고 JSON 보고서 생성

# v0.7.0-m10

###### 2026/08/26

* `힌트` 공개 기능은 Protocol 1.1 / Entry API 2를 유지합니다. 1.2 신규 기능은 호스트에 구현되기 전까지 개방되지 않습니다
* `새 기능` JVM Source Protocol 1.2 기능 제안을 추가하고 호스트 검토에 제출: 제한된 클립보드, 사용자 승인 문서, 호스트 대리 HTTPS, 호스트 관리 알림
* `새 기능` 「인가 → payload 검증 → 디스패치 → 응답 검증」 4단계 호스트 호출 파이프라인 추가. 기존 `app.launch`와 `toast.show`는 동작 변화 없이 이관 완료
* `개선` 동결 프로토콜 AAR을 schema-2 출처 잠금으로 업그레이드하고 staging 전용 갱신 스크립트와 완전한 SOP 제공
* `개선` 신구 호스트/플러그인 조합, 다운그레이드, 안정적 거부 경로를 포괄하는 7가지 1.1/1.2 프로토콜 협상 테스트 추가

# v0.6.0-m9

###### 2026/08/26

* `힌트` `Dispatchers.Main`, 완전한 `kotlin-reflect`, kotlinx-serialization은 여전히 스크립트에서 사용 불가
* `새 기능` 정확히 고정된 `kotlinx-coroutines-core-jvm` 1.11.0을 스크립트 라이브러리에 추가. 구조화 동시성과 협조적 취소 지원
* `새 기능` 바로 실행 가능한 코루틴 예제 `samples/coroutines.kt` 추가
* `개선` 런타임 지문과 컴파일 캐시 키에 코루틴 라이브러리 식별 정보를 포함하여 업그레이드 후 이전 캐시 전체 자동 무효화

##### 더 많은 릴리스 이력은 다음을 참조

* [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.changelog/CHANGELOG-ko.md)

******

### 빌드

******

저장소는 동결된 프로토콜 AAR (`protocol/`)을 포함하므로 AutoJs6 체크아웃 없이 오프라인 빌드가 가능합니다. JDK 21 권장, Android SDK에는 platforms 24와 36이 필요합니다. Debug 빌드:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 빌드:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

빌드 매개변수는 `version.properties`에 집중되어 있습니다: 현재 버전 0.7.0-m10 (build 7), minSdk 26, targetSdk 36.

Release/debug APK는 호스트가 수락하려면 AutoJs6와 동일 인증서로 서명되어야 합니다. 로컬 서명 자료는 버전 관리에서 제외된 `sign.properties`와 `app/sm003.jks`에 있습니다. 전체 게이트 명령과 릴리스 절차는 [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md) 참조.

******

### 리소스 구조

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml`은 플러그인 이름과 설명의 현지화를 제공합니다. README와 CHANGELOG는 `.python/generate_markdown.py`가 JSON 소스에서 생성합니다. 문서를 수정하려면 생성된 Markdown이 아니라 JSON 소스를 편집하세요.

******

### 관련 링크

******

- AutoJs6 문서: https://docs.autojs6.com
- AutoJs6 프로젝트 홈: https://github.com/SuperMonster003/AutoJs6
- 자매 플러그인 Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Kotlin 공식 프로젝트: https://github.com/JetBrains/kotlin
- 샘플 디렉터리: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- 프로젝트 로드맵 (마일스톤별 검증 기록 포함): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- 서드파티 고지: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
