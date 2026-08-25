# Kotlin error samples / Kotlin 错误样例

These files intentionally fail at a specific provider stage. To reproduce one in AutoJs6, copy its
contents into a `.kt` document; the current Protocol 1.1 host sends it as `Main.kt` with entry class
`<package>.Main`. They are executable regression fixtures, not Gradle application sources.

这些文件会在指定阶段**故意失败**。在 AutoJs6 中复现时，把某个文件的内容复制到 `.kt` 文档运行即可；
当前 Protocol 1.1 宿主会将其规范化为 `Main.kt`，入口为 `<包名>.Main`。它们是回归样例，不属于插件
自身的 Gradle 编译源码。

| Sample | Expected public result | 中文说明 / 修复方式 |
|---|---|---|
| `missing-import.kt` | `COMPILATION_FAILED / COMPILATION`; located `KOTLIN_ERROR` containing `UUID` | 缺少 `import java.util.UUID`；诊断应定位到第 6 行，而不是泄漏插件私有路径。 |
| `type-mismatch.kt` | `COMPILATION_FAILED / COMPILATION`; located `KOTLIN_ERROR` containing `mismatch` | 第 5 行把 `Int` 赋给 `String`；改为字符串或修改声明类型。 |
| `entry-interface-missing.kt` | `ENTRY_POINT_MISSING / COMPILATION` | `Main` 编译成功但未实现 `AutoJsJvmEntry`；为类添加接口并实现 `run`。 |
| `package-entry-mismatch.kt` | `INVALID_REQUEST / INPUT` with `Kotlin package does not match the requested entry class` | 样例声明 `sample.actual`；测试故意请求 `sample.claimed.Main`，应在编译前拒绝。 |

Compiler wording remains the pinned K2 compiler's stable English output because Protocol 1.1 has no
diagnostic-locale negotiation. The source file, line, column, error code, and this bilingual guide
provide the Chinese-facing remediation path without translating or weakening the compiler message.
