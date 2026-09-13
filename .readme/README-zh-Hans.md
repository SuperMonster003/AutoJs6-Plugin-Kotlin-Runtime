<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用于 AutoJs6 的 Kotlin 2.3.21 单文件源码编译与运行插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/commit/17f42fa7aa2a2046c74e558f313b7510d155f365"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言 (Languages)

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### 简介

******

AutoJs6 Kotlin Runtime 插件让 AutoJs6 可以直接编译并运行单文件 Kotlin 源码 (`.kt`). 插件内置 Kotlin/JVM 2.3.21 编译器 (K2) 与 D8 8.13.17 字节码转换器, 编译产物在一次性 worker 进程中执行; 编译器与脚本都不会进入 AutoJs6 进程.

本插件与 [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) 互为姊妹插件, 可同时安装并各自服务于 Kotlin / Java 源码; AutoJs6 会按语言分别记忆所选的编译组件.

******

### 功能

******

- 提供 `org.autojs.plugin.JVM_SOURCE` 编译/执行服务与 `org.autojs.plugin.INFO` 插件中心发现服务, 均受签名保护并运行于独立辅助进程.
- 内置 Kotlin/JVM 2.3.21 编译器 (K2), 脚本目标字节码 JVM 1.8, 经 D8 8.13.17 转换为 DEX 后执行.
- 支持四项逐次授权的宿主能力桥: 控制台实时输出 `console().log/error`, 应用启动 `app().launch`, 可中断休眠 `sleep`, 消息浮窗 `toast`.
- 支持 Kotlin 标准库与 `kotlinx-coroutines-core-jvm` 1.11.0 结构化并发 (`Dispatchers.Default` / `IO` / `Unconfined`).
- 带鉴权的编译缓存: 相同源码热命中中位数约 46 ms, 冷编译约 577 ms (实测约 12.5 倍加速), 执行中位数约 30 ms.
- 编译错误保留 K2 原始诊断与源码行列位置; BOM 头、Windows 路径差异、中文与 emoji 截断等场景均有确定性处理.
- 每次执行都在全新的一次性 worker 进程中进行, 结束即回收; 在宿主中停止脚本可即时中断休眠与协程.
- README 与 CHANGELOG 支持简体中文/繁体中文 (香港/台湾)/英语/法语/西班牙语/日语/韩语/俄语/阿拉伯语十种语言.

******

### 快速上手

******

- **怎么装** — 从 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) 下载 APK 并安装, 或按下方「构建」小节本地构建. 注意: 插件必须与 AutoJs6 使用相同证书签名, GitHub Actions 产出的临时证书 debug APK 仅供构建检查, 无法用于真机集成; 宿主 AutoJs6 版本号需不低于 5276.
- **怎么启用** — JVM 源码运行目前是 AutoJs6 的实验性功能: 需在宿主中开启该实验开关, 并为 Kotlin 语言显式选择本插件作为编译组件. 未开启或未选择时, 运行会分别提示稳定错误码 `JVM_SOURCE_EXPERIMENT_DISABLED` 与 `JVM_SOURCE_PROVIDER_NOT_SELECTED`.
- **怎么跑** — 在 AutoJs6 编辑器中新建 `.kt` 文件, 编写一个实现 `AutoJsJvmEntry` 接口的入口类后点击运行 (见下方「使用示例」). 当前宿主会把源码统一命名为 `Main.kt`, 入口类简单名固定为 `Main`; 可选携带普通 ASCII 包名与 import 语句.
- **出错了看哪里** — 编译失败时控制台会显示 K2 诊断与行列位置; 缺 import、类型不匹配、未实现入口接口、包名与请求不符四类常见错误的双语讲解见 [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). 运行期失败只给出稳定错误码 (如 `JVM_SOURCE_COMPILE_FAILED`、`JVM_SOURCE_TIMEOUT`), 不泄露内部路径.

******

### 使用示例

******

下面是一个可直接运行的最小示例, 演示当前全部四项宿主能力:

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

`console().log/error` 在脚本运行期间逐行实时回传; `sleep` 可被停止操作即时中断. 更多示例见 [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples) 目录: 能力冒烟 `m5-capabilities.kt`, 取消演示 `m6-cancellation.kt`, 协程示例 `coroutines.kt`.

******

### 能力边界

******

为保证安全与行为可预期, 当前版本刻意保持以下边界:

- 仅支持单文件 Kotlin 源码, 上限 4 MiB; 暂不支持多文件工程、class 文件、JAR 或 DEX 输入.
- 入口类须实现 `AutoJsJvmEntry` (Entry API 2); 包名仅支持普通 ASCII 标识符, 反引号转义与非 ASCII 包名会被明确拒绝.
- 不解析任何 Maven 或第三方依赖; 脚本可用库以下方「脚本运行库」清单为准.
- 脚本字节码目标固定为 JVM 1.8, 高于 Java 8 的 class 文件会在 D8 之前被拒绝.
- worker 进程在每次执行后退休, 协程等后台任务不会在 `run` 返回后存活; 请勿使用 `GlobalScope`.
- 发布协议为 JVM Source Protocol 1.1; Protocol 1.2 目前仅为提案, 剪贴板/文档/HTTPS/通知等能力均未开放.

******

### 脚本运行库

******

脚本编译与运行可用的库是一份精确锁定的白名单:

#### 可用

- Android framework: 以 API 24 class-only 编译桩提供编译期符号; 运行期行为仍取决于设备系统版本.
- AutoJs6 JVM Entry API 2: `JvmScriptContext` 是唯一受支持的宿主桥.
- Kotlin 标准库 2.3.21 (与内置编译器同版本锁定).
- `kotlinx-coroutines-core-jvm` 1.11.0: 支持 `runBlocking`、结构化 `async`、`delay` 与 `Dispatchers.Default` / `IO` / `Unconfined`.

#### 不可用

- `kotlinx-coroutines-android` 与 `Dispatchers.Main`: worker 进程无 UI/Looper, 调度到 Main 会失败.
- 完整 `kotlin-reflect`: 仅保留标准库基础类引用, `kotlin.reflect.full.*` 不可用.
- kotlinx-serialization、协程 debug/test 模块、编译器插件以及任意 Maven 传递依赖.

******

### 安全与隔离

******

插件按「默认拒绝」原则设计, 以下限制始终生效:

- 编译器与 worker 运行于独立进程, 从不进入 AutoJs6 进程; 服务仅接受同签名宿主调用.
- 每项宿主能力 (如启动应用、浮窗) 都按请求逐项授权, 未授权能力在派发前即被拒绝.
- 源码、产物与诊断均有大小上限; 诊断只在 Unicode 码点边界截断, 不会出现半个 emoji 或畸形 UTF-8.
- 对外错误信息只含稳定错误码与净化后的文案, 不泄露私有路径、摘要或进程信息.
- 编译缓存带鉴权校验; 工具链或运行库变更会使全部旧缓存自动失效.

******

### 发行历史

******

# v0.7.1

###### 2026/09/13

* `修复` 插件中心可通过受保护入口激活新安装的插件, 显示的元数据与实际安装包一致
* `优化` 宿主激活, 插件元数据, 多语言文档与签名发布归集遵循统一插件规范

# v0.7.0

###### 2026/09/11

* `优化` 构建阶段阻止意外引入原生依赖, 并输出 JSON 校验报告

# v0.7.0-m10

###### 2026/08/26

* `提示` 发布能力保持 Protocol 1.1 / Entry API 2 不变, 所有 1.2 新能力在宿主落地前均不开放
* `新增` 新增 JVM Source Protocol 1.2 能力提案并提交宿主评审: 有界剪贴板、用户授权文档、宿主代理 HTTPS 与宿主管理的通知
* `新增` 新增「授权校验 → payload 校验 → 派发 → 响应校验」四阶段宿主调用管线, 现有 `app.launch` 与 `toast.show` 已迁移且行为不变
* `优化` 协议冻结 AAR 升级为 schema-2 来源锁定, 并提供 staging-only 刷新脚本与完整刷新 SOP
* `优化` 新增 7 组 1.1/1.2 协议协商组合测试, 覆盖新老宿主与插件互配、降级与稳定拒绝路径

##### 更多发行历史可参阅

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

******

### 构建

******

仓库自带冻结的协议 AAR (`protocol/`), 无需检出 AutoJs6 源码即可离线构建. 推荐 JDK 21, Android SDK 需提供 platforms 24 与 36. Debug 构建:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release 构建:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

构建参数集中于 `version.properties`: 当前版本 0.7.1-m10 (build 23), minSdk 26, targetSdk 36.

Release/debug APK 必须与 AutoJs6 同证书签名才能被宿主接受; 本地签名材料位于被版本控制忽略的 `sign.properties` 与 `app/sm003.jks`. 完整门禁命令与发布流程见 [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md).

******

### 资源结构

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` 提供插件名称与描述的本地化; README 与 CHANGELOG 由 `.python/generate_markdown.py` 根据 JSON 源文件生成. 修改文档请编辑 JSON 源文件而非生成的 Markdown.

******

### 相关链接

******

- AutoJs6 文档: https://docs.autojs6.com
- AutoJs6 项目主页: https://github.com/SuperMonster003/AutoJs6
- 姊妹插件 Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Kotlin 官方项目: https://github.com/JetBrains/kotlin
- 示例目录: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- 项目路线图 (含各里程碑验证记录): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- 第三方组件声明: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
