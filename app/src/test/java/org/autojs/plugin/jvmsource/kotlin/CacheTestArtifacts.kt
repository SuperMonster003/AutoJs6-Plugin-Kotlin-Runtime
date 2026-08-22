package org.autojs.plugin.jvmsource.kotlin

import java.io.File
import javax.tools.ToolProvider

internal object CacheTestArtifacts {
    fun java8MainClass(root: File, packageName: String? = null): ByteArray {
        val source = root.resolve("Main.java").apply {
            writeText(
                """
                ${packageName?.let { "package $it;" }.orEmpty()}
                import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
                import org.autojs.plugin.jvmsource.api.JvmScriptContext;
                public final class Main implements AutoJsJvmEntry {
                    public Main() {}
                    public Object run(JvmScriptContext context) { return Boolean.TRUE; }
                }
                """.trimIndent(),
            )
        }
        val output = root.resolve("test-classes").apply { check(mkdir()) }
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Unit tests require a JDK compiler" }
        val exit = compiler.run(
            null,
            null,
            null,
            "-source",
            "8",
            "-target",
            "8",
            "-proc:none",
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            output.absolutePath,
            source.absolutePath,
        )
        check(exit == 0) { "Unable to create the Java 8 cache test artifact" }
        val packagePath = packageName?.replace('.', File.separatorChar).orEmpty()
        return output.resolve(packagePath).resolve("Main.class").readBytes()
    }
}
