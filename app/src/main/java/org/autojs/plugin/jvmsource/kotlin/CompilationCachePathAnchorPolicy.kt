package org.autojs.plugin.jvmsource.kotlin

/** Pure comparison for the Android writer's pre/post-open parent directory identity. */
internal object CompilationCachePathAnchorPolicy {
    fun unchanged(
        deviceBefore: Long,
        inodeBefore: Long,
        deviceAfter: Long,
        inodeAfter: Long,
    ): Boolean = deviceBefore >= 0L && inodeBefore > 0L &&
        deviceBefore == deviceAfter && inodeBefore == inodeAfter
}
