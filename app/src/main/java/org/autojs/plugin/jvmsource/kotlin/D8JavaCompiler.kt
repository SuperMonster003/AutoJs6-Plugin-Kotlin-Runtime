package org.autojs.plugin.jvmsource.kotlin

import android.annotation.TargetApi
import android.os.Build
import com.android.tools.r8.CompilationFailedException
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import java.io.File
import java.io.IOException

internal class D8JavaCompiler(private val runtimeLibraries: D8RuntimeLibraries) {
    fun compile(
        programJar: File,
        outputDirectory: File,
        minApi: Int,
        ensureActive: () -> Unit,
    ): File {
        ensureActive()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runWithPathApi(programJar, outputDirectory, minApi)
            } else {
                D8.main(arguments(programJar, outputDirectory, minApi))
            }
        } catch (error: CompilationFailedException) {
            throw failure(error.message ?: "D8 rejected the Kotlin/JVM bytecode", error)
        } catch (error: IOException) {
            throw failure(error.message ?: "D8 could not access its private workspace", error)
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: Throwable) {
            throw failure(error.message ?: "D8 failed to produce Android DEX", error)
        }
        ensureActive()
        return JavaDexOutputPolicy.requireSingleDexFile(outputDirectory.listFiles()?.asList().orEmpty())
    }

    internal fun arguments(programJar: File, outputDirectory: File, minApi: Int): Array<String> = buildList {
        add("--debug")
        add("--min-api")
        add(minApi.toString())
        add("--output")
        add(outputDirectory.absolutePath)
        runtimeLibraries.files.forEach { library ->
            add("--lib")
            add(library.absolutePath)
        }
        add(programJar.absolutePath)
    }.toTypedArray()

    @TargetApi(Build.VERSION_CODES.O)
    private fun runWithPathApi(programJar: File, outputDirectory: File, minApi: Int) {
        val builder = D8Command.builder()
            .addProgramFiles(programJar.toPath())
            .setOutput(outputDirectory.toPath(), OutputMode.DexIndexed)
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(minApi)
        runtimeLibraries.files.forEach { builder.addLibraryFiles(it.toPath()) }
        D8.run(builder.build())
    }

    private fun failure(message: String, cause: Throwable? = null) = JavaProviderFailure(
        JvmSourceErrorCode.DEXING_FAILED,
        JvmSourceFailurePhase.DEXING,
        message,
        cause,
    )

    companion object {
        fun optionsIdentity(minApi: Int): List<String> = listOf(
            "mode=debug",
            "output=dex-indexed",
            "min-api=$minApi",
            "libraries=controlled-runtime",
            "single-program-jar=true",
        )
    }
}
