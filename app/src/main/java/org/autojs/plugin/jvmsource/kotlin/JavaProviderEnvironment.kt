package org.autojs.plugin.jvmsource.kotlin

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.kotlin.service.JavaSourceCompilerService
import org.autojs.plugin.jvmsource.kotlin.service.CompilerProcessMemoryIsolation
import java.io.File

internal class JavaProviderEnvironment private constructor(
    val compilerClasspath: CompilerClasspath,
    val d8RuntimeLibraries: D8RuntimeLibraries,
    val runtimeLibraryFingerprint: JvmSha256,
    val compilationCache: CompilationArtifactCache?,
    val compilationCacheOperationLane: CompilationCacheOperationLane?,
    val compilationCacheEnablement: CompilationCacheEnablement,
    val compilationCacheTelemetry: CompilationCacheTelemetry,
    val installedIdentity: ProviderInstalledIdentity,
    private val installedIdentityResolver: () -> ProviderInstalledIdentity,
) {
    init {
        require((compilationCache == null) == (compilationCacheOperationLane == null))
    }

    fun resolveCurrentInstalledIdentity(): ProviderInstalledIdentity = installedIdentityResolver()

    companion object {
        @Volatile
        private var cached: JavaProviderEnvironment? = null

        fun get(context: Context): JavaProviderEnvironment = cached ?: synchronized(this) {
            cached ?: create(context.applicationContext).also { cached = it }
        }

        private fun create(context: Context): JavaProviderEnvironment {
            val compilerClasspath = CompilerClasspath.install(context)
            val d8Libraries = D8RuntimeLibraries.controlled(compilerClasspath)
            val compilerComponent = ComponentName(context, JavaSourceCompilerService::class.java)
            val cacheEnablement = CompilationCacheEnablementPolicy.evaluate(
                providerDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                compilerNonDumpable = CompilerProcessMemoryIsolation.wasNonDumpableEnforced(),
            )
            val installedIdentity = ProviderInstalledIdentityResolver.resolve(context, compilerComponent)
            val compilationCache = if (cacheEnablement == CompilationCacheEnablement.ENABLED) {
                CompilationArtifactCache(
                    File(
                        AndroidPrivateDirectoryAnchor.codeCache(context),
                        "jvm-source-kotlin-artifact-cache-v1",
                    ),
                    fileWriter = AndroidCompilationCacheFileWriter,
                    treeCleaner = AndroidCompilationCacheTreeCleaner,
                )
            } else {
                null
            }
            return JavaProviderEnvironment(
                compilerClasspath,
                d8Libraries,
                d8Libraries.fingerprint,
                compilationCache,
                compilationCache?.let { CompilationCacheOperationLane() },
                cacheEnablement,
                CompilationCacheTelemetry(),
                installedIdentity,
                { ProviderInstalledIdentityResolver.resolve(context, compilerComponent) },
            )
        }
    }
}
