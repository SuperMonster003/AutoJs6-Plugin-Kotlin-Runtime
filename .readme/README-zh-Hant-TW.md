<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Kotlin 2.3.21 單檔案原始碼編譯與執行插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/commit/17f42fa7aa2a2046c74e558f313b7510d155f365"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言 (Languages)

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### 簡介

******

AutoJs6 Kotlin Runtime 插件讓 AutoJs6 可以直接編譯並執行單檔案 Kotlin 原始碼 (`.kt`). 插件內建 Kotlin/JVM 2.3.21 編譯器 (K2) 與 D8 8.13.17 位元組碼轉換器, 編譯產物在一次性 worker 程序中執行; 編譯器與指令碼都不會進入 AutoJs6 程序.

本插件與 [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) 互為姊妹插件, 可同時安裝並各自服務於 Kotlin / Java 原始碼; AutoJs6 會按語言分別記住所選的編譯元件.

******

### 功能

******

- 提供 `org.autojs.plugin.JVM_SOURCE` 編譯/執行服務與 `org.autojs.plugin.INFO` 插件中心發現服務, 均受簽章保護並執行於獨立輔助程序.
- 內建 Kotlin/JVM 2.3.21 編譯器 (K2), 指令碼目標位元組碼 JVM 1.8, 經 D8 8.13.17 轉換為 DEX 後執行.
- 支援四項逐次授權的宿主能力橋: 主控台即時輸出 `console().log/error`, 應用啟動 `app().launch`, 可中斷休眠 `sleep`, 訊息浮窗 `toast`.
- 支援 Kotlin 標準函式庫與 `kotlinx-coroutines-core-jvm` 1.11.0 結構化並行 (`Dispatchers.Default` / `IO` / `Unconfined`).
- 帶鑑權的編譯快取: 相同原始碼熱命中中位數約 46 ms, 冷編譯約 577 ms (實測約 12.5 倍加速), 執行中位數約 30 ms.
- 編譯錯誤保留 K2 原始診斷與原始碼行列位置; BOM 頭、Windows 路徑差異、中文與 emoji 截斷等情境均有確定性處理.
- 每次執行都在全新的一次性 worker 程序中進行, 結束即回收; 在宿主中停止指令碼可即時中斷休眠與協程.
- README 與 CHANGELOG 支援簡體中文/繁體中文 (香港/台灣)/英語/法語/西班牙語/日語/韓語/俄語/阿拉伯語十種語言.

******

### 快速上手

******

- **怎麼裝** — 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) 下載 APK 並安裝, 或按下方「建置」小節本地建置. 注意: 插件必須與 AutoJs6 使用相同憑證簽章, GitHub Actions 產出的臨時憑證 debug APK 只供建置檢查, 無法用於真機整合; 宿主 AutoJs6 版本號需不低於 5276.
- **怎麼啟用** — JVM 原始碼執行目前是 AutoJs6 的實驗性功能: 需在宿主中開啟該實驗開關, 並為 Kotlin 語言明確選擇本插件作為編譯元件. 未開啟或未選擇時, 執行會分別提示穩定錯誤碼 `JVM_SOURCE_EXPERIMENT_DISABLED` 與 `JVM_SOURCE_PROVIDER_NOT_SELECTED`.
- **怎麼跑** — 在 AutoJs6 編輯器中新建 `.kt` 檔案, 編寫一個實作 `AutoJsJvmEntry` 介面的進入點類別後點擊執行 (見下方「使用範例」). 目前宿主會把原始碼統一命名為 `Main.kt`, 進入點類別簡單名固定為 `Main`; 可選攜帶普通 ASCII 套件名與 import 陳述式.
- **出錯了看哪裡** — 編譯失敗時主控台會顯示 K2 診斷與行列位置; 缺 import、型別不匹配、未實作進入點介面、套件名與請求不符四類常見錯誤的雙語講解見 [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). 執行期失敗只給出穩定錯誤碼 (如 `JVM_SOURCE_COMPILE_FAILED`、`JVM_SOURCE_TIMEOUT`), 不洩漏內部路徑.

******

### 使用範例

******

下面是一個可直接執行的最小範例, 演示目前全部四項宿主能力:

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

`console().log/error` 在指令碼執行期間逐行即時回傳; `sleep` 可被停止操作即時中斷. 更多範例見 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples) 目錄: 能力冒煙 `m5-capabilities.kt`, 取消演示 `m6-cancellation.kt`, 協程範例 `coroutines.kt`.

******

### 能力邊界

******

為保證安全與行為可預期, 目前版本刻意保持以下邊界:

- 只支援單檔案 Kotlin 原始碼, 上限 4 MiB; 暫不支援多檔案專案、class 檔案、JAR 或 DEX 輸入.
- 進入點類別須實作 `AutoJsJvmEntry` (Entry API 2); 套件名只支援普通 ASCII 識別字, 反引號跳脫與非 ASCII 套件名會被明確拒絕.
- 不解析任何 Maven 或第三方相依; 指令碼可用函式庫以下方「指令碼執行函式庫」清單為準.
- 指令碼位元組碼目標固定為 JVM 1.8, 高於 Java 8 的 class 檔案會在 D8 之前被拒絕.
- worker 程序在每次執行後退役, 協程等背景工作不會在 `run` 返回後存活; 請勿使用 `GlobalScope`.
- 發佈協定為 JVM Source Protocol 1.1; Protocol 1.2 目前只是提案, 剪貼簿/文件/HTTPS/通知等能力均未開放.

******

### 指令碼執行函式庫

******

指令碼編譯與執行可用的函式庫是一份精確鎖定的白名單:

#### 可用

- Android framework: 以 API 24 class-only 編譯樁提供編譯期符號; 執行期行為仍取決於裝置系統版本.
- AutoJs6 JVM Entry API 2: `JvmScriptContext` 是唯一受支援的宿主橋.
- Kotlin 標準函式庫 2.3.21 (與內建編譯器同版本鎖定).
- `kotlinx-coroutines-core-jvm` 1.11.0: 支援 `runBlocking`、結構化 `async`、`delay` 與 `Dispatchers.Default` / `IO` / `Unconfined`.

#### 不可用

- `kotlinx-coroutines-android` 與 `Dispatchers.Main`: worker 程序無 UI/Looper, 調度到 Main 會失敗.
- 完整 `kotlin-reflect`: 只保留標準函式庫基礎類別引用, `kotlin.reflect.full.*` 不可用.
- kotlinx-serialization、協程 debug/test 模組、編譯器外掛以及任意 Maven 遞移相依.

******

### 安全與隔離

******

插件按「預設拒絕」原則設計, 以下限制始終生效:

- 編譯器與 worker 執行於獨立程序, 從不進入 AutoJs6 程序; 服務只接受同簽章宿主呼叫.
- 每項宿主能力 (如啟動應用、浮窗) 都按請求逐項授權, 未授權能力在派發前即被拒絕.
- 原始碼、產物與診斷均有大小上限; 診斷只在 Unicode 碼位邊界截斷, 不會出現半個 emoji 或畸形 UTF-8.
- 對外錯誤訊息只含穩定錯誤碼與淨化後的文案, 不洩漏私有路徑、摘要或程序資訊.
- 編譯快取帶鑑權校驗; 工具鏈或執行函式庫變更會使全部舊快取自動失效.

******

### 發行歷史

******

# v0.7.1

###### 2026/09/13

* `修復` 外掛中心可透過受保護入口啟用新安裝的外掛, 顯示的中繼資料與實際安裝套件一致
* `優化` 宿主啟用, 外掛中繼資料, 多語言文件與簽章發佈彙整遵循統一外掛規範

# v0.7.0

###### 2026/09/11

* `優化` 建置階段阻止意外引入原生相依套件, 並輸出 JSON 校驗報告

# v0.7.0-m10

###### 2026/08/26

* `提示` 發佈能力保持 Protocol 1.1 / Entry API 2 不變, 所有 1.2 新能力在宿主落地前均不開放
* `新增` 新增 JVM Source Protocol 1.2 能力提案並提交宿主評審: 有界剪貼簿、使用者授權文件、宿主代理 HTTPS 與宿主管理的通知
* `新增` 新增「授權校驗 → payload 校驗 → 派發 → 回應校驗」四階段宿主呼叫管線, 現有 `app.launch` 與 `toast.show` 已遷移且行為不變
* `優化` 協定凍結 AAR 升級為 schema-2 來源鎖定, 並提供 staging-only 重新整理指令碼與完整重新整理 SOP
* `優化` 新增 7 組 1.1/1.2 協定協商組合測試, 涵蓋新舊宿主與插件互配、降級與穩定拒絕路徑

##### 更多發行歷史可參閱

* [CHANGELOG-zh-Hant-TW.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md)

******

### 建置

******

儲存庫自帶凍結的協定 AAR (`protocol/`), 無需檢出 AutoJs6 原始碼即可離線建置. 建議 JDK 21, Android SDK 需提供 platforms 24 與 36. Debug 建置:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 建置:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

建置參數集中於 `version.properties`: 目前版本 0.7.1-m10 (build 23), minSdk 26, targetSdk 36.

Release/debug APK 必須與 AutoJs6 同憑證簽章才能被宿主接受; 本地簽章材料位於被版本控制忽略的 `sign.properties` 與 `app/sm003.jks`. 完整門禁命令與發佈流程見 [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md).

******

### 資源結構

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` 提供插件名稱與描述的本地化; README 與 CHANGELOG 由 `.python/generate_markdown.py` 根據 JSON 來源檔案產生. 修改文件請編輯 JSON 來源檔案而非產生的 Markdown.

******

### 相關連結

******

- AutoJs6 文件: https://docs.autojs6.com
- AutoJs6 專案主頁: https://github.com/SuperMonster003/AutoJs6
- 姊妹插件 Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Kotlin 官方專案: https://github.com/JetBrains/kotlin
- 範例目錄: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- 專案路線圖 (含各里程碑驗證記錄): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- 第三方元件聲明: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
