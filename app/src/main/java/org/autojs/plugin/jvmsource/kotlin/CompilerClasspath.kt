package org.autojs.plugin.jvmsource.kotlin

import android.content.Context
import org.autojs.plugin.jvmsource.api.JvmSha256
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal data class CompilerClasspath(
    val androidJar: File,
    val entryApiJar: File,
    val kotlinStdlibJar: File,
    val identities: List<ProviderFileIdentity>,
    val fingerprint: JvmSha256,
) {
    val kotlinClasspath: String = listOf(
        androidJar,
        entryApiJar,
        kotlinStdlibJar,
    ).joinToString(File.pathSeparator) { it.absolutePath }

    fun verifyInstalled() {
        try {
            val actual = listOf(
                ProviderDigests.file(androidJar, MAX_ASSET_BYTES),
                ProviderDigests.file(entryApiJar, MAX_ASSET_BYTES),
                ProviderDigests.file(kotlinStdlibJar, MAX_ASSET_BYTES),
            )
            require(actual == identities && ProviderDigests.combine(COMPILER_CLASSPATH_DOMAIN, actual) == fingerprint)
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.ARTIFACT_INVALID,
                org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.COMPILATION,
                "Installed compiler classpath differs from its pinned runtime identity",
                error,
            )
        }
    }

    companion object {
        private const val ASSET_ROOT = "compiler-classpath"
        private const val ANDROID_JAR = "android.jar"
        private const val ENTRY_API_JAR = "entry-api.jar"
        private const val KOTLIN_STDLIB_JAR = "kotlin-stdlib.jar"
        internal const val MAX_ASSET_BYTES = 64L * 1024L * 1024L

        fun install(context: Context): CompilerClasspath {
            val privateCodeCache = AndroidPrivateDirectoryAnchor.codeCache(context)
            val lexicalRoot = File(privateCodeCache, "jvm-source-compiler-classpath").absoluteFile
            if (!lexicalRoot.exists() && !lexicalRoot.mkdirs()) {
                throw IOException("Failed to create the private compiler classpath directory")
            }
            val root = lexicalRoot.canonicalFile
            if (root.path != lexicalRoot.path || !root.isDirectory) {
                throw IOException("Compiler classpath root must be an ordinary private directory")
            }
            val androidJar = installAsset(context, root, ANDROID_JAR)
            val entryApiJar = installAsset(context, root, ENTRY_API_JAR)
            val kotlinStdlibJar = installAsset(context, root, KOTLIN_STDLIB_JAR)
            val identities = listOf(
                ProviderDigests.file(androidJar, MAX_ASSET_BYTES),
                ProviderDigests.file(entryApiJar, MAX_ASSET_BYTES),
                ProviderDigests.file(kotlinStdlibJar, MAX_ASSET_BYTES),
            )
            return CompilerClasspath(
                androidJar = androidJar,
                entryApiJar = entryApiJar,
                kotlinStdlibJar = kotlinStdlibJar,
                identities = identities.toList(),
                fingerprint = ProviderDigests.combine(COMPILER_CLASSPATH_DOMAIN, identities),
            )
        }

        private fun installAsset(context: Context, root: File, name: String): File {
            require('/' !in name && '\\' !in name) { "Compiler asset name must be a path segment" }
            val destination = File(root, name).canonicalFile
            val expectedPrefix = root.path + File.separator
            require(destination.path.startsWith(expectedPrefix)) { "Compiler asset escaped its private root" }

            val temporary = File(root, ".$name.${UUID.randomUUID()}.tmp")
            try {
                context.assets.open("$ASSET_ROOT/$name").use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            written = Math.addExact(written, read.toLong())
                            if (written > MAX_ASSET_BYTES) {
                                throw IOException("Compiler classpath asset exceeds its limit: $name")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.fd.sync()
                    }
                }
                if (!temporary.setReadOnly()) {
                    throw IOException("Failed to make compiler classpath asset read-only: $name")
                }
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Failed to replace compiler classpath asset: $name")
                }
                if (!temporary.renameTo(destination)) {
                    throw IOException("Failed to install compiler classpath asset: $name")
                }
                return destination
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }

        internal const val COMPILER_CLASSPATH_DOMAIN = "org.autojs.jvm-source.kotlin.compiler-classpath.v1"
    }
}
