<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 向け Kotlin 2.3.21 単一ファイルソースのコンパイル/実行プラグイン</p>

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

### 言語 (Languages)

******

現在の README.md は以下の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### はじめに

******

AutoJs6 Kotlin Runtime プラグインを使うと, AutoJs6 で単一ファイルの Kotlin ソースコード (`.kt`) を直接コンパイルして実行できます. プラグインは Kotlin/JVM 2.3.21 コンパイラ (K2) と D8 8.13.17 バイトコード変換器を内蔵し, コンパイル成果物は使い捨ての worker プロセスで実行されます. コンパイラもスクリプトも AutoJs6 のプロセス内では決して動きません.

本プラグインは [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) と姉妹プラグインの関係にあり, 同時にインストールしてそれぞれ Kotlin / Java ソースを担当できます. AutoJs6 は言語ごとに選択したコンパイラコンポーネントを記憶します.

******

### 機能

******

- `org.autojs.plugin.JVM_SOURCE` コンパイル/実行サービスと `org.autojs.plugin.INFO` プラグインセンター発見サービスを提供. いずれも署名保護され, 独立した補助プロセスで動作.
- Kotlin/JVM 2.3.21 コンパイラ (K2) を内蔵. スクリプトは JVM 1.8 バイトコードを対象とし, D8 8.13.17 で DEX に変換して実行.
- 個別に認可される 4 つのホスト能力ブリッジに対応: コンソールのリアルタイム出力 `console().log/error`, アプリ起動 `app().launch`, 中断可能な `sleep`, `toast` メッセージ.
- Kotlin 標準ライブラリと `kotlinx-coroutines-core-jvm` 1.11.0 の構造化並行処理 (`Dispatchers.Default` / `IO` / `Unconfined`) に対応.
- 認証付きコンパイルキャッシュ: 同一ソースのウォームヒット中央値は約 46 ms, コールドコンパイルは約 577 ms (実機計測で約 12.5 倍高速). 実行中央値は約 30 ms.
- コンパイルエラーは K2 の元の診断と行/列位置を保持. BOM ヘッダ, Windows パス差異, 中国語/絵文字の切り詰めも決定論的に処理.
- 毎回の実行は新しい使い捨て worker プロセスで行われ, 終了後に破棄. ホストからスクリプトを停止すると sleep やコルーチンは即座に中断.
- README と CHANGELOG は簡体字中国語/繁体字中国語 (香港/台湾)/英語/フランス語/スペイン語/日本語/韓国語/ロシア語/アラビア語の 10 言語に対応.

******

### クイックスタート

******

- **インストール** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) から APK をダウンロードしてインストールするか, 下記「ビルド」節に従ってローカルビルドします. 注意: プラグインは AutoJs6 と同一証明書で署名されている必要があります. GitHub Actions が生成する一時証明書の debug APK はビルド確認専用で, 実機統合には使えません. ホスト AutoJs6 のバージョンコードは 5276 以上が必要です.
- **有効化** — JVM ソース実行は現在 AutoJs6 の実験的機能です: ホストで実験スイッチを有効にし, Kotlin 言語のコンパイラコンポーネントとして本プラグインを明示的に選択してください. 未設定の場合, 実行時にそれぞれ安定エラーコード `JVM_SOURCE_EXPERIMENT_DISABLED` / `JVM_SOURCE_PROVIDER_NOT_SELECTED` が表示されます.
- **実行** — AutoJs6 エディタで `.kt` ファイルを新規作成し, `AutoJsJvmEntry` インターフェースを実装したエントリクラスを書いて実行をタップします (下記の使用例参照). 現在のホストはソース名を `Main.kt` に正規化し, エントリの単純名は `Main` に固定されます. 通常の ASCII パッケージと import 文は任意です.
- **トラブルシューティング** — コンパイル失敗時はコンソールに K2 診断と行/列位置が表示されます. よくある 4 つのエラー (import 不足, 型不一致, エントリインターフェース未実装, パッケージ不一致) の二言語解説は [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors) にあります. 実行時の失敗は安定エラーコード (`JVM_SOURCE_COMPILE_FAILED`, `JVM_SOURCE_TIMEOUT` など) のみを表示し, 内部パスは漏らしません.

******

### 使用例

******

現在の 4 つのホスト能力すべてを実演する, すぐ実行できる最小例です:

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

`console().log/error` はスクリプト実行中に一行ずつリアルタイムで転送されます. `sleep` は停止操作で即座に中断できます. その他の例は [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples) ディレクトリを参照: 能力スモーク `m5-capabilities.kt`, キャンセルデモ `m6-cancellation.kt`, コルーチン例 `coroutines.kt`.

******

### 機能境界

******

安全で予測可能な動作を保つため, 現行バージョンは意図的に以下の境界を維持しています:

- 単一ファイルの Kotlin ソースのみ対応, 上限 4 MiB. 複数ファイルプロジェクト, class ファイル, JAR, DEX 入力は未対応.
- エントリクラスは `AutoJsJvmEntry` (Entry API 2) を実装する必要があります. パッケージ名は通常の ASCII 識別子のみ対応で, バッククォートエスケープや非 ASCII パッケージは明示的に拒否されます.
- Maven やサードパーティ依存は一切解決されません. スクリプトで使えるライブラリは下記「スクリプトランタイム」の一覧に限られます.
- スクリプトのバイトコードターゲットは JVM 1.8 固定. Java 8 を超える class ファイルは D8 の前に拒否されます.
- worker プロセスは毎回の実行後に破棄されるため, コルーチン等のバックグラウンド処理は `run` の戻り後に生存しません. `GlobalScope` は使わないでください.
- 公開プロトコルは JVM Source Protocol 1.1 です. Protocol 1.2 は提案段階のみで, クリップボード/ドキュメント/HTTPS/通知などの能力は未開放です.

******

### スクリプトランタイム

******

スクリプトのコンパイルと実行で使えるライブラリは, 厳密に固定されたホワイトリストです:

#### 利用可能

- Android framework: コンパイル時シンボルは API 24 の class-only スタブ由来. 実行時の挙動はデバイスの OS バージョンに依存.
- AutoJs6 JVM Entry API 2: `JvmScriptContext` が唯一サポートされるホストブリッジ.
- Kotlin 標準ライブラリ 2.3.21 (内蔵コンパイラとバージョン固定).
- `kotlinx-coroutines-core-jvm` 1.11.0: `runBlocking`, 構造化 `async`, `delay`, `Dispatchers.Default` / `IO` / `Unconfined` に対応.

#### 利用不可

- `kotlinx-coroutines-android` と `Dispatchers.Main`: worker プロセスに UI/Looper がないため Main へのディスパッチは失敗.
- 完全な `kotlin-reflect`: 標準ライブラリの基本クラス参照のみ残り, `kotlin.reflect.full.*` は非対応.
- kotlinx-serialization, コルーチンの debug/test モジュール, コンパイラプラグイン, 任意の Maven 推移的依存.

******

### セキュリティと分離

******

プラグインはデフォルト拒否で設計されており, 以下の制限が常に有効です:

- コンパイラと worker は独立プロセスで動作し, AutoJs6 のプロセスには決して入りません. サービスは同一署名ホストの呼び出しのみ受け付けます.
- 各ホスト能力 (アプリ起動, toast など) はリクエストごとに認可され, 未認可の能力はディスパッチ前に拒否されます.
- ソース, 成果物, 診断にはすべてサイズ上限があります. 診断は Unicode コードポイント境界でのみ切り詰められ, 絵文字が半分になったり不正な UTF-8 になることはありません.
- 外部向けエラーメッセージは安定エラーコードとサニタイズ済みテキストのみで, 私有パス, ダイジェスト, プロセス情報は漏れません.
- コンパイルキャッシュは認証付きで, ツールチェーンやランタイムライブラリの変更で旧キャッシュは全て自動無効化されます.

******

### リリース履歴

******

# v0.7.0-m10

###### 2026/08/26

* `ヒント` 公開能力は Protocol 1.1 / Entry API 2 のまま. 1.2 の新能力はホスト側で実装されるまで開放されません
* `新機能` JVM Source Protocol 1.2 の能力提案を追加しホストのレビューに提出: 有界クリップボード, ユーザー許可ドキュメント, ホスト代理 HTTPS, ホスト管理の通知
* `新機能` 「認可 → payload 検証 → ディスパッチ → レスポンス検証」の 4 段階ホスト呼び出しパイプラインを追加. 既存の `app.launch` と `toast.show` は挙動を変えずに移行済み
* `改善` 凍結プロトコル AAR を schema-2 来歴ロックに更新し, staging 専用リフレッシュスクリプトと完全な SOP を追加
* `改善` 新旧ホスト/プラグインの組み合わせ, ダウングレード, 安定拒否経路を網羅する 7 組の 1.1/1.2 プロトコル交渉テストを追加

# v0.6.0-m9

###### 2026/08/26

* `ヒント` `Dispatchers.Main`, 完全な `kotlin-reflect`, kotlinx-serialization は引き続きスクリプトから利用不可
* `新機能` 厳密に固定した `kotlinx-coroutines-core-jvm` 1.11.0 をスクリプトライブラリに追加. 構造化並行処理と協調キャンセルに対応
* `新機能` すぐ実行できるコルーチン例 `samples/coroutines.kt` を追加
* `改善` ランタイム指紋とコンパイルキャッシュキーにコルーチンライブラリの識別を組み込み, 更新後は旧キャッシュを全て自動無効化

# v0.5.0-m8

###### 2026/08/25

* `新機能` よくある 4 つのコンパイルエラーの二言語解説サンプルを追加: import 不足, 型不一致, エントリインターフェース未実装, パッケージ不一致
* `修正` Windows のパス区切り文字の差異により K2 の行/列位置が失われる問題を修正
* `修正` 先頭の UTF-8 BOM を除去しても最初の行の位置がずれないように修正
* `改善` パッケージポリシーの文言を明確化: 通常の ASCII 識別子のみ対応で, 拒否時は誤解を招くエラーではなく安定した読みやすいメッセージを表示
* `改善` 診断は UTF-8 予算で管理し, Unicode コードポイント境界でのみ切り詰め — 絵文字が半分になることはありません

##### さらに詳しい履歴はこちら

* [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.changelog/CHANGELOG-ja.md)

******

### ビルド

******

リポジトリは凍結済みプロトコル AAR (`protocol/`) を同梱しており, AutoJs6 のチェックアウトなしでオフラインビルドできます. JDK 21 推奨, Android SDK には platforms 24 と 36 が必要です. Debug ビルド:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release ビルド:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

ビルドパラメータは `version.properties` に集約: 現在のバージョン 0.7.0-m10 (build 7), minSdk 26, targetSdk 36.

Release/debug APK はホストに受け入れられるために AutoJs6 と同一証明書での署名が必須です. ローカル署名素材はバージョン管理対象外の `sign.properties` と `app/sm003.jks` にあります. ゲートコマンドとリリース手順の全容は [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md) を参照.

******

### リソース構成

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` はプラグイン名と説明のローカライズを提供します. README と CHANGELOG は `.python/generate_markdown.py` が JSON ソースから生成します. ドキュメントの変更は生成済み Markdown ではなく JSON ソースを編集してください.

******

### 関連リンク

******

- AutoJs6 ドキュメント: https://docs.autojs6.com
- AutoJs6 プロジェクトホーム: https://github.com/SuperMonster003/AutoJs6
- 姉妹プラグイン Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Kotlin 公式プロジェクト: https://github.com/JetBrains/kotlin
- サンプルディレクトリ: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- プロジェクトロードマップ (マイルストーンごとの検証記録付き): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- サードパーティ通知: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md
