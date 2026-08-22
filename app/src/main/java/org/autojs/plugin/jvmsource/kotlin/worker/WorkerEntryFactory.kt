package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

/** Loads the verified DEX entry without allowing a parent-first shadow class to run. */
internal object WorkerEntryFactory {
    /** Gate 2b: ask ART to resolve the requested entry, then bind it to the exact user loader. */
    fun loadFromArt(
        userDexClassLoader: ClassLoader,
        entryClassName: String = "Main",
    ): ArtLoadedEntry {
        // Loading must not initialize the entry: provenance and ABI are checked before user code can run.
        val entryClass = try {
            Class.forName(entryClassName, false, userDexClassLoader)
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.CLASS_LOADING_FAILED,
                JvmSourceFailurePhase.WORKER_START,
                "Unable to load the requested Java entry",
                error,
            )
        }
        if (entryClass.classLoader !== userDexClassLoader) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.CLASS_LOADING_FAILED,
                JvmSourceFailurePhase.WORKER_START,
                "Java entry was not defined by the verified user DEX class loader",
            )
        }
        if (!AutoJsJvmEntry::class.java.isAssignableFrom(entryClass) ||
            !Modifier.isPublic(entryClass.modifiers) || Modifier.isAbstract(entryClass.modifiers)
        ) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
                JvmSourceFailurePhase.WORKER_START,
                "Java entry does not implement the entry ABI",
            )
        }
        val constructor = try {
            entryClass.getConstructor()
        } catch (error: ReflectiveOperationException) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
                JvmSourceFailurePhase.WORKER_START,
                "Java entry has no public no-argument constructor",
                error,
            )
        }
        return ArtLoadedEntry(entryClass, constructor, userDexClassLoader)
    }

    fun instantiate(loadedEntry: ArtLoadedEntry): AutoJsJvmEntry {
        return try {
            loadedEntry.constructor.newInstance() as AutoJsJvmEntry
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.EXECUTION_FAILED,
                JvmSourceFailurePhase.EXECUTION,
                "Java entry constructor failed",
                error,
            )
        }
    }
}

internal class ArtLoadedEntry internal constructor(
    val entryClass: Class<*>,
    internal val constructor: Constructor<*>,
    val definingClassLoader: ClassLoader,
) {
    val stage: WorkerDexLoadStage = WorkerDexLoadStage.ART_ENTRY_CLASS_LOADED
}
