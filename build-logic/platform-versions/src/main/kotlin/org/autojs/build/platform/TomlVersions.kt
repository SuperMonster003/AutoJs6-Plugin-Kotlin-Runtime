package org.autojs.build.platform

import java.io.File

/**
 * Reads the `[versions]` table of a Gradle version catalog.
 *
 * The catalog cannot be consumed through the regular accessors this early, because
 * settings plugins run before the catalog is available, so the table is parsed
 * directly. Only `[versions]` is needed, and only its plain string entries.
 *
 * zh-CN: 读取 Gradle version catalog 的 `[versions]` 表. Settings 插件的执行时机早于
 * catalog 可用, 无法走常规访问器, 因此直接解析文件; 只需要 `[versions]` 段的普通字符串条目.
 */
object TomlVersions {

    private val keyValuePattern = Regex("""^\s*([A-Za-z0-9._-]+)\s*=\s*"(.*?)"\s*(#.*)?$""")

    /** Parses the version catalog at [file], returning an empty map when absent. */
    fun read(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()

        val versions = mutableMapOf<String, String>()
        var inVersions = false
        for (raw in file.readLines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                inVersions = line == "[versions]"
                continue
            }
            if (!inVersions) continue
            keyValuePattern.find(line)?.let { match ->
                val (_, key, value) = match.groupValues
                versions[key] = value
            }
        }
        return versions
    }

}
