package org.autojs.plugin.jvmsource.kotlin

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import java.io.File

internal data class RawKotlinDiagnostic(
    val severity: JvmDiagnosticSeverity,
    val message: String,
    val sourcePath: String?,
    val line: Int?,
    val column: Int?,
)

internal data class KotlinCompilationResult(
    val succeeded: Boolean,
    val diagnostics: List<RawKotlinDiagnostic>,
)

internal fun interface KotlinCompilerInvoker {
    fun exec(
        collector: MessageCollector,
        services: Services,
        arguments: K2JVMCompilerArguments,
    ): ExitCode
}

private val embeddedKotlinCompilerInvoker = KotlinCompilerInvoker { collector, services, arguments ->
    K2JVMCompiler().apply {
        isReadingSettingsFromEnvironmentAllowed = false
    }.exec(collector, services, arguments)
}

internal object KotlinCompilerFailureDiagnostic {
    fun message(error: Throwable): String = when (error) {
        is LinkageError -> "Kotlin compiler runtime is incompatible with this Android environment"
        is OutOfMemoryError -> "Kotlin compiler exhausted its isolated process memory"
        else -> "Kotlin compiler failed before source analysis"
    }
}

internal object KotlinCompilerDiagnosticPolicy {
    private const val DISABLED_FAST_JAR_FILE_SYSTEM_INFO =
        "Using outdated version of JAR FS: it might make your build slower"

    fun shouldPublish(severity: CompilerMessageSeverity, message: String): Boolean =
        severity != CompilerMessageSeverity.INFO ||
            message.trim() != DISABLED_FAST_JAR_FILE_SYSTEM_INFO
}

internal class KotlinJvmCompiler(
    private val classpath: CompilerClasspath,
    private val invoker: KotlinCompilerInvoker = embeddedKotlinCompilerInvoker,
) {
    fun compile(
        sourceFile: File,
        outputDirectory: File,
        ensureActive: () -> Unit,
    ): KotlinCompilationResult {
        val collector = CollectingMessageCollector()
        ensureActive()
        val exitCode = try {
            invoker.exec(
                collector,
                Services.EMPTY,
                arguments(sourceFile, outputDirectory),
            )
        } catch (error: Throwable) {
            collector.report(
                CompilerMessageSeverity.ERROR,
                KotlinCompilerFailureDiagnostic.message(error),
                null,
            )
            ExitCode.INTERNAL_ERROR
        }
        ensureActive()
        return KotlinCompilationResult(
            succeeded = exitCode == ExitCode.OK && !collector.hasErrors(),
            diagnostics = collector.values.toList(),
        )
    }

    internal fun arguments(sourceFile: File, outputDirectory: File) = K2JVMCompilerArguments().apply {
        destination = outputDirectory.absolutePath
        classpath = this@KotlinJvmCompiler.classpath.kotlinClasspath
        kotlinHome = checkNotNull(this@KotlinJvmCompiler.classpath.androidJar.parentFile).absolutePath
        noJdk = true
        noStdlib = true
        noReflect = true
        includeRuntime = false
        jvmTarget = JVM_TARGET
        moduleName = MODULE_NAME
        javaParameters = false
        disableStandardScript = true
        useFastJarFileSystem = false
        freeArgs = listOf(sourceFile.absolutePath)
    }

    private class CollectingMessageCollector : MessageCollector {
        val values = mutableListOf<RawKotlinDiagnostic>()
        private var errors = false

        override fun clear() {
            values.clear()
            errors = false
        }

        override fun hasErrors(): Boolean = errors

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?,
        ) {
            if (!KotlinCompilerDiagnosticPolicy.shouldPublish(severity, message)) return
            val publicSeverity = when {
                severity.isError -> JvmDiagnosticSeverity.ERROR
                severity.isWarning -> JvmDiagnosticSeverity.WARNING
                severity == CompilerMessageSeverity.INFO -> JvmDiagnosticSeverity.INFO
                else -> return
            }
            if (publicSeverity == JvmDiagnosticSeverity.ERROR) errors = true
            values += RawKotlinDiagnostic(
                severity = publicSeverity,
                message = if (severity == CompilerMessageSeverity.EXCEPTION) {
                    "Kotlin compiler failed before source analysis"
                } else {
                    message
                },
                sourcePath = location?.path,
                line = location?.line?.takeIf { it > 0 },
                column = location?.column?.takeIf { it > 0 },
            )
        }
    }

    companion object {
        const val JVM_TARGET = "1.8"
        const val MODULE_NAME = "autojs_jvm_source"

        val CACHE_OPTIONS_IDENTITY: List<String> = listOf(
            "compiler=kotlin-jvm-k2",
            "compiler-runtime=android-headless-compat-v1",
            "jvm-target=$JVM_TARGET",
            "kotlin-home=controlled-provider-directory",
            "jdk=controlled-android-stubs",
            "stdlib=controlled-provider-asset",
            "reflect=disabled",
            "scripts=disabled",
            "compiler-plugins=disabled",
            "debug=default-source-lines",
        )
    }
}
