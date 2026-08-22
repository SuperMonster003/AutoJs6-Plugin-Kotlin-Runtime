package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.kotlin.ValidatedDexArtifact
import org.autojs.plugin.jvmsource.kotlin.WorkerDexLoaderKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class WorkerDexLoaderCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadedDexCleanupRemovesItsOrdinaryPrivateTree() {
        val root = temporaryFolder.newFolder("worker-dex")
        root.resolve("classes.dex").writeBytes(byteArrayOf(1))

        val released = LoadedDex(
            javaClass.classLoader!!,
            validatedArtifact(),
            WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
            cleanupRoot = root,
        ).closeAndVerifyTemporaryStorageReleased()

        assertTrue(released)
        assertFalse(root.exists())
    }

    @Test
    fun loadedDexCleanupNeverFollowsANestedSymlinkWhenSupported() {
        val root = temporaryFolder.newFolder("worker-dex-link")
        val outside = temporaryFolder.newFolder("worker-outside")
        val protected = outside.resolve("protected.txt").apply { writeText("keep") }
        val link = root.resolve("escape")
        if (runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isFailure) return

        val released = LoadedDex(
            javaClass.classLoader!!,
            validatedArtifact(),
            WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
            cleanupRoot = root,
        ).closeAndVerifyTemporaryStorageReleased()

        assertTrue(released)
        assertFalse(root.exists())
        assertTrue(protected.isFile)
    }

    @Test
    fun inMemoryLoaderPositivelyReportsNoTemporaryDexTree() {
        assertTrue(
            LoadedDex(
                javaClass.classLoader!!,
                validatedArtifact(WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER),
                WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            ).closeAndVerifyTemporaryStorageReleased(),
        )
    }

    private fun validatedArtifact(
        loaderKind: WorkerDexLoaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
    ) = ValidatedDexArtifact(
        sizeBytes = 112L,
        sha256 = JvmSha256.digest(byteArrayOf(1)),
        version = "037",
        classDescriptors = setOf("LMain;"),
        requestMinApi = 24,
        deviceApi = 24,
        loaderKind = loaderKind,
    )
}
