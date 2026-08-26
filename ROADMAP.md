# Roadmap — AutoJs6 Kotlin Runtime Plugin

> 当前发布候选: `0.6.0-m9` (VERSION_BUILD 6) · Protocol 1.1 · Entry API 2 · 要求宿主 ≥ 5276
>
> M9 功能与本地发布门禁已完成 (2026-08-26): 受控运行库纳入精确锁定的 coroutines
> core-jvm 1.11.0，Android/reflect 模块保持排除；145 个单测、双 Lint、Protocol 三门禁、
> 31.85 MB release 及指定真机结构化协程/取消 Binder 用例 1/1 全绿。Private CI、标签与
> 标签归档闭环将在 release commit 后补记。
>
> M8 已完成 (2026-08-26): 三份源码/工具链决策、四类双语错误样例、141 个单测、
> 双 Lint、Protocol 三门禁及指定真机 Binder 诊断用例 1/1 全绿；Private CI、
> `v0.5.0-m8` annotated tag 与标签归档隔离重建也已闭环。
>
> M7 已完成 (2026-08-25): release APK 30.38 MB（较 M6 降 32.99%）；真机冷编译/
> 缓存命中中位数 577/46 ms（约 12.5x）；release/debug 各 50 会话及四类故障注入全绿；
> 124 个单测、双 Lint、Protocol 三门禁、Private CI、`v0.4.0-m7` 标签归档隔离重建均通过。
>
> M6 已完成 (2026-08-25): 119 个单测、离线双 APK 与 Android test APK、严格 Lint、
> 协议正常/损坏/连线三门禁、platform-versions 1.4.1 源码快照测试及 `v0.3.0-m6`
> 标签归档隔离重建均通过；指定真机的四能力、缓存与主动取消冒烟通过，Private
> GitHub 冷/热缓存 CI 均为绿色，annotated tag 已推送。

## 一、能力现状评估（结论）

**协议 1.1 能力面已 100% 覆盖，插件在当前冻结协议下已"功能完备"：**

| 维度 | 协议定义 | 插件实现 | 状态 |
|---|---|---|---|
| 脚本能力 | `APP_LAUNCH` / `CONSOLE_STREAM` / `SLEEP` / `TOAST` | `RemoteJvmScriptContext` 全部实现并逐项校验授权 | ✅ 全覆盖 |
| 隔离能力 | 编译/执行分进程、每次执行新 worker、宽限期后强杀 | `:compiler` + `:worker` 双进程、`SingleUseWorkerLifecycle`、终止策略 | ✅ 全覆盖 |
| 发现服务 | `org.autojs.plugin.INFO` + `org.autojs.plugin.JVM_SOURCE` | 两个签名保护服务均已导出 | ✅ 全覆盖 |
| 语言 | 协议枚举含 KOTLIN/JAVA | 本插件声明 KOTLIN（JAVA 由姊妹插件承担） | ✅ 符合定位 |
| 编译缓存 | 协议未强制 | 完整实现（键、落盘、清理、遥测、启用策略） | ✅ 超出协议 |

**因此，继续精进的空间不在"补功能"，而在五个方向：**
1. **工程化维护** — M6 已落地 build-logic、CI、CHANGELOG、发布清单与 Lint 基线，后续按同一门禁持续维护;
2. **体积与性能** — M7 已把 release 降至 30.38 MB，建立冷/热基准、遥测与 50 会话资源门禁;
3. **语言与诊断体验** — M8 已明确 ASCII 包名/当前宿主 `Main` 边界，补齐双语错误样例与位置/Unicode 门禁，并记录保持 jvmTarget 1.8 的升级前置条件;
4. **受控运行库扩展** — M9 已把精确锁定的 coroutines core-jvm 纳入编译/D8/运行时指纹，Android Main 与完整反射保持排除;
5. **协议 1.2 协同** — 新能力（剪贴板/存储/HTTP 等）需与宿主联动演进，本仓库需演练协议刷新流程。

## 二、验证约定（网络受限环境）

- 本地验证**一律默认 `--offline`**，标准命令:
  `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :m7-harness:assembleDebug --offline`
- 需要联网的条目显式标注 **[需联网]**，集中在单一时间窗口执行（一次 `--refresh-dependencies` 拉全，之后回到离线）; 遇 Cloudflare 502/524/529 仅重试该窗口，不阻塞其他条目。
- GitHub Actions 属云端网络，不受本地网络约束，视为离线条目。

---

## M6 — 工程化收尾与发布流水线（`0.3.0-m6`）

### 6.1 落地 build-logic 迁移
- [x] 审查并落地 platform-versions 插件迁移: `settings.gradle.kts` / `build.gradle.kts` / `version.properties` / `gradle/libs.versions.toml` / `build-logic/`
- [x] 将未公开的 platform-versions 1.4.1 作为带来源提交与 MPL 许可的 included-build 源码快照入库候选，移除 Maven Local 冷启动依赖
- [x] 将迁移与 M6 收尾改动审查后提交为 release commit `2d8498e`；后续仅有 CI 环境与确定性修正 `1ffdaa9` / `d4db645`
- [x] 迁移后离线全量验证通过（标准命令三连）
- [x] 确认 `verifyPinnedInputs` 仍是 assemble 前置依赖，并新增正常摘要、一字节损坏、assemble 连线三项回归任务
- [x] README「Local build」小节与新构建方式核对无出入

### 6.2 版本与变更管理
- [x] 新增 `CHANGELOG.md`（Keep a Changelog 格式），回填 M1–M5 里程碑要点
- [x] 新增 `docs/RELEASE_CHECKLIST.md`: 版本号递增（NAME+BUILD 同步）→ 双 APK 签名一致性（与 AutoJs6 同证书）→ `adb install -r` 冒烟 → 官方索引 schema-v2 元数据核对
- [x] 用当前候选树生成临时 `git archive`，在隔离目录完成 platform 插件测试 + 协议门禁 + 离线单测/双 APK/Lint 重建
- [x] release commit 完成后创建并推送 annotated tag `v0.3.0-m6`（tag object `687689d` → commit `d4db645`），并按清单对标签归档完成独立离线复验

### 6.3 CI（云端执行，不受本地网络影响）
- [x] 新增 GitHub Actions: push/PR 触发 `testDebugUnitTest` + `assembleDebug` + `assembleDebugAndroidTest` + `lintDebug`（启用 Gradle 缓存与有界 step 级重试）
- [x] CI 上传 test/Lint report 与临时证书签名的 debug APK 为 artifacts
- [x] CI 独立 job 运行 platform 插件测试与协议正常/损坏/连线三门禁
- [x] 推送后确认 GitHub-hosted runner 首次冷构建与缓存命中构建均为绿色：冷构建 [`32809108755`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/actions/runs/32809108755)，热缓存复跑 [`32809865814`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/actions/runs/32809865814)

### 6.4 静态质量门
- [x] `lintDebug --offline` 跑通并生成 `lint-baseline.xml` 入库；消除 13 个可修项，保留 14 个历史坐标项，禁用 3 类时变版本提示，其他新增告警视为红线
- [ ] （可选）detekt/ktlint 离线配置，规则集最小化，仅卡新增代码

### 6.5 发布候选验证
- [x] 宿主、debug 与 release APK 同签名；`0.3.0-m6` / build 3 / SDK 元数据及 release SHA-256 已固化
- [x] `QV710AF65F` 生产链路四能力冒烟通过；冷/热两次执行分别为 7.107 秒与 1.157 秒
- [x] 通过宿主 `engines.stopAll()` 主动取消，独立 worker 在 500 ms 内退出且 7 秒内未复活
- [x] 真机运行 Kotlin 编译 → D8 → Dex 校验/加载 → 入口调用仪器测试，1/1 通过
- [x] 发布证据见 [`docs/releases/0.3.0-m6.md`](docs/releases/0.3.0-m6.md)

**M6 完成判据**: ✅ 已满足（2026-08-25）。工作区无未提交改动、CI 绿、CHANGELOG/RELEASE_CHECKLIST 就位、离线三连命令与标签归档复验通过；6.4 的 detekt/ktlint 为明确的可选后续项。

---

## M7 — 体积与性能（`0.4.0-m7`）

### 7.1 APK 体积
- [x] 固化 M6/M7 debug 与 release 基线及 `apkanalyzer` top-10 到 [`docs/perf/size-baseline.md`](docs/perf/size-baseline.md)：release 从 45,339,694 B 降至 30,383,822 B（-32.99%）
- [x] 评估 `isShrinkResources`：该项要求同时启用 R8；因 R8 兼容性门未通过，保持关闭并记录可复现证据，不以牺牲编译器完整性换体积
- [x] 实测 R8 compat：`minifyReleaseWithR8` 产生 144 条覆盖缺失完整 JDK/编译器可选接口的 `-dontwarn` 建议；判定宽泛抑制不可接受，恢复 `isMinifyEnabled=false` / `isShrinkResources=false`
- [x] 设定 release ≤ 40 MB 预算；确定性 class-only API 24 编译类路径使当前 30.38 MB 候选留有约 9.62 MB 余量

### 7.2 编译时延与缓存效果
- [x] 新增同一源码冷编译/缓存命中各 5 次的真机基准与记录表 [`docs/perf/compile-latency.md`](docs/perf/compile-latency.md)：编译中位数 577/46 ms，执行中位数均为 30 ms
- [x] 核对 `CompilationCacheTelemetry`：基准 5/5/5/0、release 混压 44/6/1/0 均与请求序列完全一致，并把典型值写入 README 与精确单测
- [x] 产出 [`docs/decisions/compiler-lifecycle.md`](docs/decisions/compiler-lifecycle.md)：保留同一 host binding epoch 内的热缓存，逐次 dispose Kotlin 环境，final unbind 退休专用 `:compiler`；INFO 服务隔离到 `:discovery`

### 7.3 稳定性压测（设备端）
- [x] 连环 50 次混合会话全绿：release 40 成功/5 编译失败/5 取消、40 个不同 worker PID 且代数递增；debug 资源审计 workspace 始终 0、FD 79→78、PSS 增长 32,834,560 B（预算 ≤64 MiB）
- [x] 故障注入四件套均返回契约错误码/阶段：`SOURCE_TOO_LARGE / INPUT`、`TIMEOUT / COMPILATION`、`TIMEOUT / EXECUTION`、`WORKER_DIED / EXECUTION`
- [x] 压测 harness 与 [`scripts/stress/`](scripts/stress/) 入库，PowerShell 一键链路已在 `QV710AF65F` 实跑并恢复 release；完整证据见 [`docs/perf/stress-and-faults.md`](docs/perf/stress-and-faults.md)
- [x] release commit `acc27d4`、annotated tag `v0.4.0-m7`（tag object `e4d161b`）、Private CI [`32822203503`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/actions/runs/32822203503) 与标签归档独立离线重建全绿；发布证据见 [`docs/releases/0.4.0-m7.md`](docs/releases/0.4.0-m7.md)

**M7 完成判据**: ✅ 已满足（2026-08-25）。体积基线/预算和 R8 决策有据可复现；缓存遥测与真机五对基准一致；release/debug 50 会话、资源预算、四类故障注入及进程退休全部通过；Private CI、annotated tag 与标签源码归档复验闭环完成。

---

## M8 — 源码形态与诊断体验（`0.5.0-m8`）

### 8.1 源码策略演进（纯插件侧，无需协议变更）
- [x] 非 ASCII / 反引号转义包名维持拒绝，并以稳定公开文案明确“仅支持普通 ASCII 标识符”；宿主、类/JAR/DEX 边界需协同后才可扩展，决策见 [`docs/decisions/source-layout.md`](docs/decisions/source-layout.md)
- [x] 入口类名灵活性评估完成：当前 AutoJs6 宿主恒发 `Main.kt` / `<package>.Main`，插件仍按协议请求字段驱动；替代 `ScriptEntry.kt` / `ScriptEntry` 已由单测和真实 Binder 真机用例验证
- [x] `EntryClassAnalyzer` 边界补测完成：多顶层类、嵌套同名类、object/interface/abstract、缺构造器、入口缺失/歧义均有精确错误码与文案；同时钉住 Java 8 class major 52 上限
- [x] 单文件 size 临界用例完成：`MAX_SOURCE_BYTES`（4 MiB）恰好接受，超出 1 字节返回 `SOURCE_TOO_LARGE / INPUT`

### 8.2 诊断质量
- [x] `KotlinDiagnosticSanitizer` 快照扩充：真实 K2 与真机均证明 BOM 剥离后首行位置为 `1:19`；修复 Windows 分隔符差异导致同一源码位置被误删的问题，外部路径仍拒绝
- [x] `EncodedDiagnosticBudget` 中文/emoji 截断边界完成：完整 wire payload 二分预算只在 Unicode 码点边界截断，无半个代理项、替换字符或畸形 UTF-8
- [x] 常见错误中文可读性走查完成：缺 import、类型不匹配、未实现 `AutoJsJvmEntry`、包名与入口不符四类双语说明及可执行 fixture 已入库 [`samples/errors/`](samples/errors/)
- [x] `samples/m5-capabilities.kt` 已补 `app().launch` 演示，并在 M6 指定真机生产链路中验证返回 `true`

### 8.3 工具链前瞻（决策型条目，产出记录即完成）
- [x] `jvmTarget 1.8 → 11/17` 调研完成：M8 保持 1.8，明确 class major、D8、API 26/31 真机、缓存身份与完整发布门禁后才可升级，见 [`docs/decisions/jvm-target.md`](docs/decisions/jvm-target.md)
- [x] Kotlin 2.3.x → 后续升级 SOP 完成：当前保持 2.3.21；每次升级必须依次通过形状锁定 patch → runtime verify → 离线/归档/真机全测，见 [`docs/decisions/kotlin-compiler-upgrade.md`](docs/decisions/kotlin-compiler-upgrade.md)

### 8.4 私有发布闭环

- [x] release commit `7f42864`、annotated tag `v0.5.0-m8`（tag object `d402147`）及 peeled commit 已在 Private remote 双向核验
- [x] Private CI [`32867662740`](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/actions/runs/32867662740) 双 job 全绿；标签源码归档在隔离目录完成 7-task platform 测试与 193-task 离线重建，141/141 单测及双 Lint 再次通过
- [x] canonical release APK、同签名、设备回拉哈希、Binder 五场景、进程退休与私有发布边界均固化在 [`docs/releases/0.5.0-m8.md`](docs/releases/0.5.0-m8.md)

**M8 完成判据**: ✅ 已满足（2026-08-26）。三份决策记录和四类双语错误 fixture 就位；141/141 单测、冻结协议、双 APK、Android-test、双 Lint 离线门禁全绿；`QV710AF65F` release Binder 用例 1/1 通过并确认三个辅助进程全部退休；Private CI、annotated tag、标签源码归档与发布证据记录闭环完成。

---

## M9 — 受控运行库扩展（`0.6.0-m9`）**[需联网: 一次性拉取新依赖]**

> 现状: worker 运行库白名单仅 `android.jar + jvm-source-api + kotlin-stdlib`（见 `D8RuntimeLibraries.controlled`），脚本不可用协程、反射、序列化。

- [x] 决策采纳 `kotlinx-coroutines-core-jvm:1.11.0`，拒绝 Android Main 模块；DEX/包体、取消桥接、线程与单次 worker 进程边界记录在 [`controlled-runtime-libraries.md`](docs/decisions/controlled-runtime-libraries.md)
- [x] **[需联网窗口已关闭]** 单窗口精确拉取并核验 1,577,052-byte JAR（SHA-256 `d1d75a…04fa`）；四文件白名单、runtime/toolchain 指纹 v2 与缓存整体失效均有交叉用例
- [x] 新增可执行 [`samples/coroutines.kt`](samples/coroutines.kt)；真实 K2→JAR→D8 单测与 `QV710AF65F` release Binder 协程/取消用例 1/1 通过
- [x] `kotlin-reflect` 独立决策为不纳入脚本 classpath：仅保留 stdlib 基础类引用，完整反射扩展继续禁用并有编译失败用例
- [x] README「脚本运行库矩阵」明确 core/dispatcher 可用面与 Android Main、reflect、serialization、compiler plugin 不可用面
- [x] 回归: 145/145 单测、离线双 APK + Android-test + Harness、双 Lint、Protocol 三门禁、platform snapshot 与缓存新旧指纹交叉用例全绿

**M9 完成判据**: ✅ 功能与本地发布门禁已满足（2026-08-26）。决策记录、受控运行库矩阵、精确依赖锁、指纹/缓存失效测试与可执行样例就位；release 31.85 MB（较 M8 +4.84%，低于 40 MB 预算），指定真机结构化结果、Main 缺席、主动取消、设备回拉哈希及三个辅助进程退休均已验证。Private CI、annotated tag、标签归档与最终发布证据在 release commit 后闭环。

---

## M10 — 协议 1.2 协同与新能力（与宿主联动，节奏受宿主约束）

> 本仓库消费 `protocol/` 冻结 AAR，新能力必须先在宿主侧落地协议，再刷新快照。

- [ ] 整理并向宿主提交「能力扩展提案」: 候选按优先级——`clipboard.read/write`、`storage`（宿主授权的局部文件读写）、`http.request`（宿主代理出网）、`notification.post`、`engines.exec`（脚本互调）; 每项含权限模型与 payload 上限建议
- [ ] 演练协议刷新 SOP: 宿主侧生成三 AAR → 同步更新 `protocol-artifacts.lock.json`（`sourceDirty=false`）→ `verifyPinnedInputs` 通过 → 记录为 `docs/PROTOCOL_REFRESH.md`
- [ ] 双版本协商测试: `JvmProviderInfo.protocolMin/Max` 覆盖 1.1↔1.2 组合（老宿主+新插件 / 新宿主+老插件）
- [ ] 新能力落地时，`RemoteJvmScriptContext` 按「授权校验 → payload 校验 → dispatch → 响应校验」四段式模板扩展，每个能力独立单测
- [ ] Entry API 3 前瞻跟踪（仅当宿主推进时展开）

**M10 完成判据**: 提案已提交宿主、刷新 SOP 文档化并演练一次、协商矩阵用例全绿。

---

## 持续任务（不绑定里程碑，每次触发即勾选记录）

- [ ] Kotlin/D8 版本升级一律走「补丁三件套」回归: `patchKotlinCompilerForAndroid` 形状断言 → `verifyKotlinCompilerRuntime` → 离线全量单测
- [ ] 每新增脚本能力同步新增 `samples/` 样例与 README 说明
- [ ] `values` / `values-zh-rCN` 字符串资源双语同步检查
- [ ] 每个里程碑收尾: 版本号（NAME 里程碑后缀 + BUILD 递增）、CHANGELOG、tag 三件事一次完成
