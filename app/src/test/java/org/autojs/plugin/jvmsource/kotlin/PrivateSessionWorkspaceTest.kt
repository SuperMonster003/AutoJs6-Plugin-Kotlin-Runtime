package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.LinkOption

class PrivateSessionWorkspaceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reportsZeroEligibleEvidenceOnlyAfterVerifiedRemoval() {
        val base = temporaryFolder.newFolder("verified-session-cleanup")
        val workspace = PrivateSessionWorkspace.createUnder(base)

        assertTrue(workspace.closeAndVerifyRemoved())
        assertTrue(base.list().orEmpty().isEmpty())
    }

    @Test
    fun removesOnlyExactStaleSessionDirectories() {
        val base = temporaryFolder.newFolder("sessions")
        val stale = base.resolve("session-00000000-0000-0000-0000-000000000001")
            .apply { check(mkdir()) }
        stale.resolve("Main.java").writeText("stale")
        val unrelated = base.resolve("session-not-a-uuid").apply { check(mkdir()) }
        unrelated.resolve("keep.txt").writeText("keep")
        val ordinaryFile = base.resolve("session-00000000-0000-0000-0000-000000000002")
            .apply { writeText("keep") }

        PrivateSessionWorkspace.clearStaleUnder(base)

        assertFalse(stale.exists())
        assertTrue(unrelated.resolve("keep.txt").isFile)
        assertTrue(ordinaryFile.isFile)
    }

    @Test
    fun neverFollowsANestedSymlinkOutsideTheSessionRootWhenSupported() {
        val base = temporaryFolder.newFolder("symlink-sessions")
        val stale = base.resolve("session-00000000-0000-0000-0000-000000000003")
            .apply { check(mkdir()) }
        val outside = temporaryFolder.newFolder("outside")
        val protected = outside.resolve("protected.txt").apply { writeText("keep") }
        val link = stale.resolve("escape")
        val rootLink = base.resolve("session-00000000-0000-0000-0000-000000000004")
        val linksCreated = runCatching {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            Files.createSymbolicLink(rootLink.toPath(), outside.toPath())
        }.isSuccess
        if (!linksCreated) return

        PrivateSessionWorkspace.clearStaleUnder(base)

        assertFalse(stale.exists())
        assertTrue(protected.isFile)
        assertTrue(Files.isSymbolicLink(rootLink.toPath()))
    }

    @Test
    fun rejectsASymlinkSessionBaseWithoutReadingDeletingOrCreatingInItsTarget() {
        val parent = temporaryFolder.newFolder("base-link-parent")
        val outside = temporaryFolder.newFolder("base-link-outside")
        val protectedSession = outside.resolve("session-00000000-0000-0000-0000-000000000005")
            .apply { check(mkdir()) }
        val protected = protectedSession.resolve("protected.txt").apply { writeText("keep") }
        val linkedBase = parent.resolve("jvm-source-java-sessions")
        if (runCatching { Files.createSymbolicLink(linkedBase.toPath(), outside.toPath()) }.isFailure) return

        assertThrows(java.io.IOException::class.java) {
            PrivateSessionWorkspace.clearStaleUnder(linkedBase)
        }
        assertThrows(java.io.IOException::class.java) {
            PrivateSessionWorkspace.createUnder(linkedBase)
        }
        assertTrue(protected.isFile)
        assertTrue(outside.listFiles().orEmpty().contentEquals(arrayOf(protectedSession)))
    }

    @Test
    fun acceptsAFrameworkPrivateDirectoryThroughATrustedAncestorAliasWhenSupported() {
        val actualData = temporaryFolder.newFolder("actual-framework-data")
        val cache = actualData.resolve("cache").apply { check(mkdir()) }
        val alias = temporaryFolder.root.resolve("framework-data-alias")
        if (runCatching { Files.createSymbolicLink(alias.toPath(), actualData.toPath()) }.isFailure) return

        val resolved = AndroidPrivateDirectoryAnchor.canonicalizeFrameworkDirectory(
            alias.resolve("cache"),
            "test cache",
            ::readOrdinaryDirectoryIdentityWithoutFollowingFinalLink,
        )

        assertTrue(resolved.path == cache.canonicalPath)
    }

    @Test
    fun rejectsAFrameworkPrivateDirectoryWhoseFinalElementIsASymlinkWhenSupported() {
        val data = temporaryFolder.newFolder("framework-data-link")
        val outside = temporaryFolder.newFolder("framework-cache-outside")
        val linkedCache = data.resolve("cache")
        if (runCatching { Files.createSymbolicLink(linkedCache.toPath(), outside.toPath()) }.isFailure) return

        assertThrows(java.io.IOException::class.java) {
            AndroidPrivateDirectoryAnchor.canonicalizeFrameworkDirectory(
                linkedCache,
                "test cache",
                ::readOrdinaryDirectoryIdentityWithoutFollowingFinalLink,
            )
        }
    }

    private fun readOrdinaryDirectoryIdentityWithoutFollowingFinalLink(
        file: java.io.File,
    ): AndroidPrivateDirectoryAnchor.DirectoryIdentity? {
        val attributes = Files.readAttributes(
            file.toPath(),
            java.nio.file.attribute.BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        if (!attributes.isDirectory) return null
        val stableIdentity = attributes.fileKey()?.hashCode()?.toLong()
            ?: file.toPath().toRealPath().toString().hashCode().toLong()
        return AndroidPrivateDirectoryAnchor.DirectoryIdentity(0L, stableIdentity)
    }
}
