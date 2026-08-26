package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSha256
import java.io.File

internal data class D8RuntimeLibraries(
    val files: List<File>,
    val identities: List<ProviderFileIdentity>,
    val fingerprint: JvmSha256,
) {
    companion object {
        fun controlled(classpath: CompilerClasspath): D8RuntimeLibraries = D8RuntimeLibraries(
            files = classpath.controlledFiles.toList(),
            identities = classpath.identities.toList(),
            fingerprint = classpath.fingerprint,
        )
    }
}
