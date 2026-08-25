package org.autojs.build.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionMapMatcherTest {

    /** The IntelliJ IDEA map as maintained in AutoJs6 settings.gradle.kts. */
    private val ideaAgpVersionMap = mapOf(
        "2026.1.2" to "9.0.1",
        "2026.1" to "8.13.2",
        "2025.2.2" to "8.12.0",
        "2025.2.1" to "8.11.1",
        "2025.1" to "8.10.1",
        "2024.3" to "8.7.3",
        "2024.2" to "8.5.2",
        "2024.1" to "8.2.2",
        "2023.3" to "8.2.2",
    )

    @Test
    fun `matches an exact map key`() {
        assertEquals("2025.2.2", VersionMapMatcher.findBestMatchingMapKey(ideaAgpVersionMap, "2025.2.2"))
    }

    @Test
    fun `matches the nearest lower key`() {
        assertEquals("2025.1", VersionMapMatcher.findBestMatchingMapKey(ideaAgpVersionMap, "2025.1.4"))
        assertEquals("2024.3", VersionMapMatcher.findBestMatchingMapKey(ideaAgpVersionMap, "2024.3.5"))
    }

    @Test
    fun `matches the newest key when the platform is newer than the whole map`() {
        // This is the silent-downgrade path the stale-map check exists to catch.
        assertEquals("2026.1.2", VersionMapMatcher.findBestMatchingMapKey(ideaAgpVersionMap, "2027.1"))
    }

    @Test
    fun `returns null when the platform is older than every key`() {
        assertNull(VersionMapMatcher.findBestMatchingMapKey(ideaAgpVersionMap, "2022.1"))
    }

    @Test
    fun `returns null for an empty map`() {
        assertNull(VersionMapMatcher.findBestMatchingMapKey(emptyMap(), "2026.2"))
    }

    @Test
    fun `detects a stale map`() {
        assertTrue(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "2027.1"))
        assertTrue(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "2026.2.2"))
    }

    @Test
    fun `does not report staleness when the map covers the platform`() {
        assertFalse(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "2026.1.2"))
        assertFalse(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "2024.1"))
    }

    @Test
    fun `reports staleness for IDEA 2026 2 now that the map tops out at 2026 1 2`() {
        // Removing the 2026.2.1 entry means 2026.2 and 2026.2.1 both look stale and
        // fall back to auto selection, which the compatibility table caps at AGP 9.0.1.
        assertTrue(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "2026.2"))
        assertTrue(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "2026.2.1"))
    }

    @Test
    fun `does not report staleness for an unknown platform version`() {
        assertFalse(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, Consts.DEFAULT_VERSION))
    }

    @Test
    fun `does not report staleness for an empty map`() {
        assertFalse(VersionMapMatcher.isPlatformNewerThanVersionMap(emptyMap(), "2026.2"))
    }

    @Test
    fun `does not report staleness for an unparsable platform version`() {
        assertFalse(VersionMapMatcher.isPlatformNewerThanVersionMap(ideaAgpVersionMap, "EAP-Nightly"))
    }

    @Test
    fun `reproduces the IDEA 2026 2 incident`() {
        // The map as it stood before the fix: highest entry was 2025.2.2 to AGP 8.12.0.
        val staleMap = ideaAgpVersionMap.filterKeys { it.startsWith("202").and(it < "2026") }
        val matched = VersionMapMatcher.findBestMatchingMapKey(staleMap, "2026.2")
        assertEquals("2025.2.2", matched)
        assertEquals("8.12.0", staleMap[matched])
        // The stale check flags exactly this situation, so callers can fall back to auto selection
        // instead of building with AGP 8.12.0, which lacks the built-in Kotlin support this needs.
        assertTrue(VersionMapMatcher.isPlatformNewerThanVersionMap(staleMap, "2026.2"))
    }

}
