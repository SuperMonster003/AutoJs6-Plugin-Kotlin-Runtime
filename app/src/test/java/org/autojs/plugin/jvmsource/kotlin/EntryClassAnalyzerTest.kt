package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class EntryClassAnalyzerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsExactlyOneConcretePublicMainEntry() {
        val classes = compile(
            """
                public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public Main() {}
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
            """.trimIndent(),
        )

        UserClassJarWriter.write(classes, temporaryFolder.root.resolve("valid.jar"))
    }

    @Test
    fun acceptsPackagedEntryAndPreservesItsClassPathInJarSummary() {
        val classes = compile(
            """
                package com.example.scripts;
                import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
                import org.autojs.plugin.jvmsource.api.JvmScriptContext;
                public final class Main implements AutoJsJvmEntry {
                    public Object run(JvmScriptContext context) { return null; }
                }
            """.trimIndent(),
            "packaged",
        )
        val jar = temporaryFolder.root.resolve("packaged.jar")

        val summary = UserClassJarWriter.write(classes, jar, "com.example.scripts.Main")
        val (_, validated) = UserClassJarValidator.validate(jar, "com.example.scripts.Main")

        assertEquals(summary, validated)
        assertTrue("Lcom/example/scripts/Main;" in summary.dexDescriptors)
    }

    @Test
    fun acceptsMultipleTopLevelClassesAndADistinctNestedClassNamedMain() {
        val classes = compile(
            """
                public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
                final class Helper {
                    static final class Main {}
                }
                final class AdditionalTopLevelType {}
            """.trimIndent(),
            "multiple-types",
        )

        val summary = UserClassJarWriter.write(
            classes,
            temporaryFolder.root.resolve("multiple-types.jar"),
        )

        assertTrue("LMain;" in summary.dexDescriptors)
        assertTrue("LHelper\$Main;" in summary.dexDescriptors)
        assertTrue("LAdditionalTopLevelType;" in summary.dexDescriptors)
    }

    @Test
    fun missingRequestedEntryHasAnActionableDiagnostic() {
        assertFailure(
            JvmSourceErrorCode.ENTRY_POINT_MISSING,
            """
                final class Other implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
            """.trimIndent(),
            "missing",
            "Requested entry class must implement AutoJsJvmEntry",
        )
    }

    @Test
    fun multipleConcreteEntriesHaveAnActionableDiagnostic() {
        assertFailure(
            JvmSourceErrorCode.ENTRY_POINT_AMBIGUOUS,
            """
                public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
                final class Other implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
            """.trimIndent(),
            "ambiguous",
            "Kotlin source must produce exactly one concrete AutoJsJvmEntry",
        )
    }

    @Test
    fun objectInterfaceAndAbstractEntryShapesAreAbiIncompatible() {
        val expected = "Entry class must be concrete and public with a public no-argument constructor"
        assertFailure(
            JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
            """
                public abstract class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {}
            """.trimIndent(),
            "abstract",
            expected,
        )
        assertFailure(
            JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
            """
                public interface Main extends org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {}
            """.trimIndent(),
            "interface",
            expected,
        )
        assertFailure(
            JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
            """
                public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public static final Main INSTANCE = new Main();
                    private Main() {}
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
            """.trimIndent(),
            "object",
            expected,
        )
        assertFailure(
            JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
            """
                public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                    public Main(String ignored) {}
                    public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) { return null; }
                }
            """.trimIndent(),
            "constructor",
            expected,
        )
    }

    @Test
    fun classFilesAboveJava8RemainOutsideTheValidatedRuntimeContract() {
        val bytes = CacheTestArtifacts.java8MainClass(temporaryFolder.newFolder("class-version"))
        bytes[6] = 0
        bytes[7] = 61 // Java 17 class-file major version.

        val failure = assertThrows(JavaProviderFailure::class.java) {
            EntryClassAnalyzer.requireSingleEntry(mapOf("Main" to bytes), "Main")
        }

        assertEquals(JvmSourceErrorCode.ARTIFACT_INVALID, failure.code)
        assertEquals("Cached JAR contains an invalid JVM class artifact", failure.message)
    }

    private fun assertFailure(
        code: JvmSourceErrorCode,
        source: String,
        name: String,
        message: String,
    ) {
        val classes = compile(source, name)
        val failure = assertThrows(JavaProviderFailure::class.java) {
            UserClassJarWriter.write(classes, temporaryFolder.root.resolve("$name.jar"))
        }
        assertEquals(code, failure.code)
        assertEquals(message, failure.message)
    }

    private fun compile(source: String, name: String = "valid"): File {
        val root = temporaryFolder.newFolder(name)
        val sourceFile = root.resolve("Main.java").apply { writeText(source) }
        val classes = root.resolve("classes").apply { mkdir() }
        val diagnostics = StringWriter()
        val entryApiLocation = File(
            AutoJsJvmEntry::class.java.protectionDomain.codeSource.location.toURI(),
        ).absolutePath
        val succeeded = BatchCompiler.compile(
            arrayOf(
                "-source", "8",
                "-target", "8",
                "-proc:none",
                "-classpath", entryApiLocation,
                "-d", classes.absolutePath,
                sourceFile.absolutePath,
            ),
            PrintWriter(diagnostics),
            PrintWriter(diagnostics),
            null,
        )
        check(succeeded) { diagnostics.toString() }
        return classes
    }
}
