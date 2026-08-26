package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompilerArgumentsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun kotlinCompilerUsesOnlyControlledClasspathAndFixedJvmProfile() {
        val root = temporaryFolder.newFolder("path with spaces")
        val androidJar = root.resolve("android.jar")
        val entryApiJar = root.resolve("entry api --flag.jar")
        val kotlinStdlibJar = root.resolve("kotlin stdlib.jar")
        val kotlinxCoroutinesCoreJar = root.resolve("coroutines core.jar")
        val identity = JvmSha256.digest(byteArrayOf(1))
        val classpath = CompilerClasspath(
            androidJar,
            entryApiJar,
            kotlinStdlibJar,
            kotlinxCoroutinesCoreJar,
            identities = listOf(
                ProviderFileIdentity("android.jar", 1L, identity),
                ProviderFileIdentity("entry-api.jar", 1L, identity),
                ProviderFileIdentity("kotlin-stdlib.jar", 1L, identity),
                ProviderFileIdentity("kotlinx-coroutines-core-jvm.jar", 1L, identity),
            ),
            fingerprint = identity,
        )
        val source = root.resolve("Main.kt")
        val output = root.resolve("classes --output")

        val arguments = KotlinJvmCompiler(classpath).arguments(source, output)
        assertEquals(output.absolutePath, arguments.destination)
        assertEquals(
            listOf(androidJar, entryApiJar, kotlinStdlibJar, kotlinxCoroutinesCoreJar)
                .joinToString(java.io.File.pathSeparator) { it.absolutePath },
            arguments.classpath,
        )
        assertEquals(listOf(source.absolutePath), arguments.freeArgs)
        assertEquals(root.absolutePath, arguments.kotlinHome)
        assertEquals("1.8", arguments.jvmTarget)
        assertTrue(arguments.noJdk)
        assertTrue(arguments.noStdlib)
        assertTrue(arguments.noReflect)
        assertTrue(arguments.disableStandardScript)
        assertFalse(arguments.includeRuntime)
    }

    @Test
    fun compilerStartupFailureBecomesAStableSourceDiagnosticWithoutLeakingItsMessage() {
        val root = temporaryFolder.newFolder("startup failure")
        val identity = JvmSha256.digest(byteArrayOf(3))
        val classpath = CompilerClasspath(
            root.resolve("android.jar"),
            root.resolve("entry-api.jar"),
            root.resolve("kotlin-stdlib.jar"),
            root.resolve("kotlinx-coroutines-core-jvm.jar"),
            identities = emptyList(),
            fingerprint = identity,
        )
        val compiler = KotlinJvmCompiler(classpath) { _, _, _ ->
            throw NoClassDefFoundError("private /data/user/0/provider/path")
        }

        val result = compiler.compile(
            root.resolve("Main.kt"),
            root.resolve("classes"),
        ) {}

        assertFalse(result.succeeded)
        assertEquals(1, result.diagnostics.size)
        assertEquals(JvmDiagnosticSeverity.ERROR, result.diagnostics.single().severity)
        assertEquals(
            "Kotlin compiler runtime is incompatible with this Android environment",
            result.diagnostics.single().message,
        )
        assertFalse(result.diagnostics.single().message.contains("/data/"))
    }

    @Test
    fun compilerReportedInternalExceptionDropsItsStackAndPrivatePaths() {
        val root = temporaryFolder.newFolder("reported internal exception")
        val identity = JvmSha256.digest(byteArrayOf(4))
        val classpath = CompilerClasspath(
            root.resolve("android.jar"),
            root.resolve("entry-api.jar"),
            root.resolve("kotlin-stdlib.jar"),
            root.resolve("kotlinx-coroutines-core-jvm.jar"),
            identities = emptyList(),
            fingerprint = identity,
        )
        val compiler = KotlinJvmCompiler(classpath) { collector, _, _ ->
            collector.report(
                org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION,
                "IllegalStateException at /data/user/0/provider/private.kt\nprivate stack",
                null,
            )
            org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR
        }

        val result = compiler.compile(
            root.resolve("Main.kt"),
            root.resolve("classes"),
        ) {}

        assertFalse(result.succeeded)
        assertEquals(1, result.diagnostics.size)
        assertEquals("Kotlin compiler failed before source analysis", result.diagnostics.single().message)
    }

    @Test
    fun disabledFastJarFileSystemInfoIsNotPublishedAsASourceDiagnostic() {
        val root = temporaryFolder.newFolder("jar fs diagnostic")
        val compiler = KotlinJvmCompiler(
            CompilerClasspath(
                root.resolve("android.jar"),
                root.resolve("entry-api.jar"),
                root.resolve("kotlin-stdlib.jar"),
                root.resolve("kotlinx-coroutines-core-jvm.jar"),
                identities = emptyList(),
                fingerprint = JvmSha256.digest(byteArrayOf(5)),
            ),
        ) { collector, _, _ ->
            collector.report(
                org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.INFO,
                "Using outdated version of JAR FS: it might make your build slower",
                null,
            )
            org.jetbrains.kotlin.cli.common.ExitCode.OK
        }

        val result = compiler.compile(
            root.resolve("Main.kt"),
            root.resolve("classes"),
        ) {}

        assertTrue(result.succeeded)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun compilerRuntimeIsRetiredAfterSuccessAndInvokerFailure() {
        val root = temporaryFolder.newFolder("compiler runtime retirement")
        val classpath = CompilerClasspath(
            root.resolve("android.jar"),
            root.resolve("entry-api.jar"),
            root.resolve("kotlin-stdlib.jar"),
            root.resolve("kotlinx-coroutines-core-jvm.jar"),
            identities = emptyList(),
            fingerprint = JvmSha256.digest(byteArrayOf(6)),
        )
        var retirements = 0
        val retire = KotlinCompilerRuntimeRetirer { retirements += 1 }
        val successful = KotlinJvmCompiler(
            classpath = classpath,
            runtimeRetirer = retire,
            invoker = KotlinCompilerInvoker { _, _, _ ->
                org.jetbrains.kotlin.cli.common.ExitCode.OK
            },
        ).compile(root.resolve("Success.kt"), root.resolve("success-classes")) {}
        val failed = KotlinJvmCompiler(
            classpath = classpath,
            runtimeRetirer = retire,
            invoker = KotlinCompilerInvoker { _, _, _ ->
                throw IllegalStateException("private compiler failure")
            },
        ).compile(root.resolve("Failure.kt"), root.resolve("failure-classes")) {}

        assertTrue(successful.succeeded)
        assertFalse(failed.succeeded)
        assertEquals(2, retirements)
    }

    @Test
    fun compilerRuntimeRetirementFailureFailsClosedWithStableDiagnostic() {
        val root = temporaryFolder.newFolder("compiler retirement failure")
        val compiler = KotlinJvmCompiler(
            classpath = CompilerClasspath(
                root.resolve("android.jar"),
                root.resolve("entry-api.jar"),
                root.resolve("kotlin-stdlib.jar"),
                root.resolve("kotlinx-coroutines-core-jvm.jar"),
                identities = emptyList(),
                fingerprint = JvmSha256.digest(byteArrayOf(7)),
            ),
            runtimeRetirer = KotlinCompilerRuntimeRetirer {
                throw IllegalStateException("private runtime retirement state")
            },
            invoker = KotlinCompilerInvoker { _, _, _ ->
                org.jetbrains.kotlin.cli.common.ExitCode.OK
            },
        )

        val result = compiler.compile(
            root.resolve("Main.kt"),
            root.resolve("classes"),
        ) {}

        assertFalse(result.succeeded)
        assertEquals("Kotlin compiler failed before source analysis", result.diagnostics.single().message)
    }

    @Test
    fun d8UsesOnlyControlledLibrariesAndKeepsEveryPathAsOneArgument() {
        val root = temporaryFolder.newFolder("d8 path with spaces")
        val androidJar = root.resolve("android --lib.jar")
        val entryApiJar = root.resolve("entry api.jar")
        val fingerprint = JvmSha256.digest(byteArrayOf(2))
        val libraries = D8RuntimeLibraries(
            files = listOf(androidJar, entryApiJar),
            identities = emptyList(),
            fingerprint = fingerprint,
        )
        val program = root.resolve("program --min-api.jar")
        val output = root.resolve("d8 output")

        val arguments = D8JavaCompiler(libraries).arguments(program, output, 24)

        assertEquals(program.absolutePath, arguments.last())
        assertTrue(arguments.contains(androidJar.absolutePath))
        assertTrue(arguments.contains(entryApiJar.absolutePath))
        assertEquals(2, arguments.count { it == "--lib" })
        assertEquals(1, arguments.count { it == "--min-api" })
        assertEquals("24", arguments[arguments.indexOf("--min-api") + 1])
    }

    @Test
    fun compilerClasspathRevalidatesItsPinnedRuntimeIdentity() {
        val root = temporaryFolder.newFolder("pinned classpath")
        val androidJar = root.resolve("android.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entryApiJar = root.resolve("entry-api.jar").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val kotlinStdlibJar = root.resolve("kotlin-stdlib.jar").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val kotlinxCoroutinesCoreJar = root.resolve("kotlinx-coroutines-core-jvm.jar").apply {
            writeBytes(byteArrayOf(10, 11, 12))
        }
        val identities = listOf(
            ProviderDigests.file(androidJar),
            ProviderDigests.file(entryApiJar),
            ProviderDigests.file(kotlinStdlibJar),
            ProviderDigests.file(kotlinxCoroutinesCoreJar),
        )
        val classpath = CompilerClasspath(
            androidJar,
            entryApiJar,
            kotlinStdlibJar,
            kotlinxCoroutinesCoreJar,
            identities,
            ProviderDigests.combine(CompilerClasspath.COMPILER_CLASSPATH_DOMAIN, identities),
        )

        classpath.verifyInstalled()
        entryApiJar.writeBytes(byteArrayOf(9))

        assertThrows(JavaProviderFailure::class.java) { classpath.verifyInstalled() }
    }
}
