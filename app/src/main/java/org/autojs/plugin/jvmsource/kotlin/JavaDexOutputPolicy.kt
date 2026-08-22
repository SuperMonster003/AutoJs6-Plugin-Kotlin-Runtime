package org.autojs.plugin.jvmsource.kotlin

/** R3 remains a single-DEX profile; a second classesN.dex is a stable rejection. */
internal object JavaDexOutputPolicy {
    fun requireSingleDexFile(files: Collection<java.io.File>): java.io.File {
        val dexFiles = files
            .filter { it.isFile && DEX_NAME.matches(it.name) }
            .sortedBy(java.io.File::getName)
        if (dexFiles.size != 1 || dexFiles.single().name != "classes.dex") {
            throw JavaProviderFailure(
                org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.DEXING_FAILED,
                org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.DEXING,
                "The Kotlin source profile requires exactly one classes.dex output",
            )
        }
        return dexFiles.single()
    }

    private val DEX_NAME = Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")
}
