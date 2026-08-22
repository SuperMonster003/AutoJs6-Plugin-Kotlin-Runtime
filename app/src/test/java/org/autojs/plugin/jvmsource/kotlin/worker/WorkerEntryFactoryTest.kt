package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerEntryFactoryTest {
    @After
    fun clearInitializationProbe() {
        System.clearProperty(INITIALIZATION_PROPERTY)
    }

    @Test
    fun rejectsParentShadowBeforeItsStaticInitializerRuns() {
        val parent = javaClass.classLoader
        val parentFirstChild = object : ClassLoader(parent) {}

        val failure = runCatching {
            WorkerEntryFactory.loadFromArt(parentFirstChild)
        }.exceptionOrNull()

        assertTrue(failure is JavaProviderFailure)
        assertEquals(JvmSourceErrorCode.CLASS_LOADING_FAILED, (failure as JavaProviderFailure).code)
        assertNull(System.getProperty(INITIALIZATION_PROPERTY))
    }

    @Test
    fun acceptsOnlyMainDefinedByTheSuppliedLoaderAndInitializesAtConstruction() {
        val parent = javaClass.classLoader
        val mainBytes = checkNotNull(parent.getResourceAsStream("Main.class")).use { it.readBytes() }
        val userLoader = MainDefiningClassLoader(parent, mainBytes)

        val loadedEntry = WorkerEntryFactory.loadFromArt(userLoader)

        assertEquals(WorkerDexLoadStage.ART_ENTRY_CLASS_LOADED, loadedEntry.stage)
        assertSame(userLoader, loadedEntry.definingClassLoader)
        assertSame(userLoader, loadedEntry.entryClass.classLoader)
        assertNull(System.getProperty(INITIALIZATION_PROPERTY))

        val entry = WorkerEntryFactory.instantiate(loadedEntry)

        assertSame(userLoader, entry.javaClass.classLoader)
        assertTrue(entry is AutoJsJvmEntry)
        assertEquals("true", System.getProperty(INITIALIZATION_PROPERTY))
    }

    @Test
    fun resolvesAQualifiedDynamicEntryName() {
        val loader = PackagedEntry::class.java.classLoader

        val loaded = WorkerEntryFactory.loadFromArt(loader, PackagedEntry::class.java.name)
        val entry = WorkerEntryFactory.instantiate(loaded)

        assertSame(PackagedEntry::class.java, loaded.entryClass)
        assertTrue(entry is PackagedEntry)
    }

    class PackagedEntry : AutoJsJvmEntry {
        override fun run(context: JvmScriptContext): Any? = null
    }

    private class MainDefiningClassLoader(
        parent: ClassLoader,
        private val mainBytes: ByteArray,
    ) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name != "Main") return super.loadClass(name, resolve)
            synchronized(this) {
                val entryClass = findLoadedClass(name)
                    ?: defineClass(name, mainBytes, 0, mainBytes.size)
                if (resolve) resolveClass(entryClass)
                return entryClass
            }
        }
    }

    private companion object {
        const val INITIALIZATION_PROPERTY = "autojs.jvm.source.test.main.initialized"
    }
}
