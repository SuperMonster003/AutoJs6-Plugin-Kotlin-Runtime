package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class UserClassJarValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rebuildsEntryAbiSummaryFromClassBytes() {
        val jar = writeJar(temporaryFolder.root.resolve("program.jar"))

        val (_, summary) = UserClassJarValidator.validate(jar)

        assertEquals(1, summary.classFileCount)
        assertEquals(setOf("LMain;"), summary.dexDescriptors)
    }

    @Test
    fun rejectsTrailingJarFramingAndUntrustedEntries() {
        val trailing = writeJar(temporaryFolder.root.resolve("trailing.jar"))
        trailing.appendBytes(byteArrayOf(1))
        assertThrows(JavaProviderFailure::class.java) { UserClassJarValidator.validate(trailing) }

        val extra = writeJar(temporaryFolder.root.resolve("extra.jar"), includeExtra = true)
        assertThrows(JavaProviderFailure::class.java) { UserClassJarValidator.validate(extra) }
    }

    private fun writeJar(destination: File, includeExtra: Boolean = false): File {
        val main = CacheTestArtifacts.java8MainClass(temporaryFolder.newFolder("compile-${destination.name}"))
        JarOutputStream(destination.outputStream().buffered()).use { output ->
            output.putNextEntry(JarEntry("Main.class").apply { time = 0L })
            output.write(main)
            output.closeEntry()
            if (includeExtra) {
                output.putNextEntry(JarEntry("metadata.txt").apply { time = 0L })
                output.write(byteArrayOf(1))
                output.closeEntry()
            }
        }
        return destination
    }
}
