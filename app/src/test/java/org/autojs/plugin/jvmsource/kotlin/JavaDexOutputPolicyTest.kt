package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JavaDexOutputPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun exactlyOneClassesDexIsAdmitted() {
        val dex = temporaryFolder.newFile("classes.dex")
        temporaryFolder.newFile("notes.txt")

        assertEquals(dex, JavaDexOutputPolicy.requireSingleDexFile(temporaryFolder.root.listFiles()!!.asList()))
    }

    @Test
    fun missingRenamedAndMultiDexOutputsAreStableRejections() {
        val empty = temporaryFolder.newFolder("empty")
        assertDexingFailure(empty.listFiles()!!.asList())

        val renamed = temporaryFolder.newFolder("renamed")
        renamed.resolve("classes2.dex").createNewFile()
        assertDexingFailure(renamed.listFiles()!!.asList())

        val multiple = temporaryFolder.newFolder("multiple")
        multiple.resolve("classes.dex").createNewFile()
        multiple.resolve("classes2.dex").createNewFile()
        assertDexingFailure(multiple.listFiles()!!.asList())
    }

    private fun assertDexingFailure(files: Collection<java.io.File>) {
        val failure = assertThrows(JavaProviderFailure::class.java) {
            JavaDexOutputPolicy.requireSingleDexFile(files)
        }
        assertEquals(JvmSourceErrorCode.DEXING_FAILED, failure.code)
    }
}
