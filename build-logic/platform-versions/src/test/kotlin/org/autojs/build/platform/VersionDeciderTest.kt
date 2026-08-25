package org.autojs.build.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the three scenarios that matter downstream: a map that covers the IDE,
 * a map that has fallen behind, and a plain command line build.
 */
class VersionDeciderTest {

    /** Reads the datasets bundled in this plugin's own resources. */
    private val dataSource = DataSource(localDataDir = null)

    private val ideaAgpVersionMap = mapOf(
        "2026.1.2" to "9.0.1",
        "2026.1" to "8.13.2",
        "2025.2.2" to "8.12.0",
        "2025.1" to "8.10.1",
        "2024.3" to "8.7.3",
        "2023.3" to "8.2.2",
    )

    private fun idea(version: String, map: Map<String, String> = ideaAgpVersionMap) = Platform(
        name = "IntelliJIdea",
        vendor = "Jetbrains",
        agpVersionMap = map,
        displayName = "IntelliJ IDEA",
    ).also { it.version = version }

    private fun commandLine() = Platform(name = "Windows 11", vendor = "unknown")

    private fun decider(platform: Platform, gradleVersion: String = "9.3.0") =
        VersionDecider(platform, dataSource, gradleVersion)

    @Test
    fun `bundled datasets load from plugin resources`() {
        assertTrue(dataSource.list("agp-releases").isNotEmpty())
        assertTrue(dataSource.props("agp-gradle-compat").isNotEmpty())
        assertTrue(dataSource.props("gradle-kotlin-compat").isNotEmpty())
        assertTrue(dataSource.props("java-gradle-compat").isNotEmpty())
    }

    @Test
    fun `scenario B - a covered IDE version follows the map`() {
        val decision = decider(idea("2026.1.2")).decideAgpVersion()
        assertEquals("9.0.1", decision.version)
        assertEquals("", decision.hintSuffix) { "an up-to-date map should be followed verbatim" }
    }

    @Test
    fun `scenario B - a covered IDE version between entries matches the nearest lower`() {
        val decision = decider(idea("2025.1.4")).decideAgpVersion()
        assertEquals("8.10.1", decision.version)
        assertTrue(decision.hintSuffix.contains(Identifier.NEAREST_LOWER_MATCHED))
    }

    @Test
    fun `scenario A - an IDE newer than the whole map falls back to auto selection`() {
        val decision = decider(idea("2027.1")).decideAgpVersion()
        // Auto selection means the newest released AGP the running Gradle can load,
        // rather than 9.0.1, the newest entry the stale map happens to know.
        assertEquals(decider(idea("2027.1")).maxSupportedAgpVersion(), decision.version)
        assertEquals(Identifier.AUTO_SPECIFIED_SUFFIX, decision.hintSuffix)
    }

    @Test
    fun `the stale-map fallback stays within one line of what the map knows`() {
        // Auto selection alone would reach AGP 9.3.0 here, past what an IDE topping out at
        // 9.1.0 accepts. The fallback is held to the line just above the newest entry.
        val map = mapOf("2026.2" to "9.1.0")
        assertEquals("9.3.0", decider(commandLine(), gradleVersion = "9.6.1").maxSupportedAgpVersion())

        val decision = decider(idea("2026.3", map), gradleVersion = "9.6.1").decideAgpVersion()
        assertEquals("9.2.1", decision.version) { "one line of headroom above 9.1 is the 9.2 line" }
        assertEquals(Identifier.AUTO_SPECIFIED_SUFFIX, decision.hintSuffix) { "a stale map means auto selection" }
    }

    @Test
    fun `a patch-level IDE update earns no extra AGP headroom`() {
        // 2026.2.1 is the same IDE line as 2026.2 and supports the same AGP. Treating it
        // as newer let it reach 9.2.1, which the IDE rejects: "Latest supported version
        // is AGP 9.1.0".
        val map = mapOf("2026.2" to "9.1.0")
        val decision = decider(idea("2026.2.1", map), gradleVersion = "9.6.1").decideAgpVersion()
        assertEquals("9.1.1", decision.version) { "a patch update stays on the 9.1 line" }

        // A minor-level update does earn a line.
        val ahead = decider(idea("2026.3", map), gradleVersion = "9.6.1").decideAgpVersion()
        assertEquals("9.2.1", ahead.version) { "a minor update moves up to the 9.2 line" }
    }

    @Test
    fun `the fallback ceiling never drops below what this Gradle can load`() {
        // A map this far behind says nothing useful about the IDE, so the ceiling gives way.
        val staleMap = mapOf("2025.1" to "8.10.1")
        val ceiling = decider(idea("2026.2", staleMap)).agpFallbackCeiling(staleMap)
        assertEquals("9.0.1", ceiling) { "8.11 is unusable on Gradle 9.3.0, so the floor applies" }
    }

    @Test
    fun `IDEA 2026 2 falls back to auto selection and still lands on the 9 0 line`() {
        // The map deliberately stops at 2026.1.2, because as of 2026.2 the JetBrains
        // Android plugin supports AGP 9.0.x rather than 9.1. Both 2026.2 and 2026.2.1
        // therefore take the stale-map path, and auto selection caps them at 9.0.1
        // anyway, so the outcome matches what the map used to state explicitly.
        listOf("2026.2", "2026.2.1").forEach { version ->
            val decision = decider(idea(version)).decideAgpVersion()
            assertEquals("9.0.1", decision.version) { "unexpected AGP for IDEA $version" }
            assertEquals(Identifier.AUTO_SPECIFIED_SUFFIX, decision.hintSuffix) {
                "IDEA $version should report the fallback"
            }
        }
    }

    @Test
    fun `reproduces the IDEA 2026 2 incident`() {
        // The map as it stood before the fix, topping out at 2025.2.2 to AGP 8.12.0.
        val staleMap = ideaAgpVersionMap.filterKeys { it < "2026" }
        val decision = decider(idea("2026.2", staleMap)).decideAgpVersion()
        // Gradle 9.3.0 caps AGP at the 9.0 line, which still carries the built-in Kotlin
        // support that AGP 8.12.0 lacks, so the build no longer breaks.
        assertEquals("9.0.1", decision.version) { "should not silently downgrade to 8.12.0" }
        assertEquals(Identifier.AUTO_SPECIFIED_SUFFIX, decision.hintSuffix) { "the fallback is reported as auto" }
    }

    @Test
    fun `scenario C - a bare command line uses auto selection`() {
        val decision = decider(commandLine()).decideAgpVersion()
        // No map at all, so the decision rests purely on the Gradle compatibility table:
        // AGP 9.1 would need Gradle 9.3.1, leaving 9.0.1 as the newest usable release.
        assertEquals("9.0.1", decision.version)
        assertTrue(decision.hintSuffix.contains(Identifier.AUTO))
    }

    @Test
    fun `caps AGP against the running Gradle version`() {
        // AGP 9.1 needs Gradle 9.3.1, so on 9.3.0 a map promising it must be capped.
        val decision = decider(idea("2026.1.2", mapOf("2026.1.2" to "9.1.1"))).decideAgpVersion()
        assertEquals("9.0.1", decision.version)
        assertTrue(decision.hintSuffix.contains(Identifier.DOWNGRADED))
    }

    @Test
    fun `no longer offers anything to a Gradle 8 build`() {
        // Support starts at AGP 9.0, which needs Gradle 9.1.0, so the compatibility
        // table has nothing a Gradle 8 build could load.
        assertEquals(null, decider(commandLine(), gradleVersion = "8.13").maxSupportedAgpVersion())
        assertEquals(null, decider(commandLine(), gradleVersion = "9.0.0").maxSupportedAgpVersion())
        // 9.1.0 is the floor, and it maps to the 9.0 line.
        assertEquals("9.0.1", decider(commandLine(), gradleVersion = "9.1.0").maxSupportedAgpVersion())
    }

    @Test
    fun `resolves a two-segment map value to a released patch version`() {
        val decision = decider(idea("2026.2", mapOf("2026.1" to "9.0"))).decideAgpVersion()
        assertEquals("9.0.1", decision.version)
    }

    @Test
    fun `an IDE predating the whole map still gets auto selection`() {
        val platform = idea("2020.1")
        val decision = decider(platform).decideAgpVersion()
        // No entry matches, so auto selection applies rather than leaving it undecided.
        assertEquals("9.0.1", decision.version)
        assertTrue(decision.hintSuffix.contains(Identifier.AUTO))
        // The last-resort fallback stays available for callers when even auto yields nothing.
        assertEquals("8.2.2", decider(platform).agpFallbackVersion())
    }

    @Test
    fun `an unknown platform version still decides a version`() {
        val platform = idea(Consts.DEFAULT_VERSION)
        val decision = decider(platform).decideAgpVersion()
        assertEquals("9.0.1", decision.version)
        assertTrue(decision.hintSuffix.contains(Identifier.AUTO))
    }

    @Test
    fun `decides Kotlin from the running Gradle version`() {
        val onGradle95 = decider(idea("2026.1.2"), gradleVersion = "9.5.0").decideKotlinVersion().version
        val onGradle90 = decider(idea("2026.1.2"), gradleVersion = "9.0.0").decideKotlinVersion().version
        assertTrue(onGradle95 != null && onGradle90 != null)
        assertTrue(VersionComparator.compareVersionStrings(onGradle95!!, onGradle90!!) > 0) {
            "a newer Gradle should allow a newer Kotlin: $onGradle95 vs $onGradle90"
        }
    }

    @Test
    fun `maps Gradle versions to the highest supported Java version`() {
        val decider = decider(commandLine())
        assertTrue(decider.getMaxSupportedJavaVersion("9.3.0") >= decider.getMaxSupportedJavaVersion("8.5"))
        assertTrue(decider.getMaxSupportedJavaVersion("8.5") >= 17)
    }

    @Test
    fun `reads the java table with the Gradle version rather than the AGP version`() {
        val decider = decider(commandLine())
        // Both original settings scripts fed the AGP version into this table. Guard the
        // fix by pinning the two results apart: 9.3.0 is a Gradle version allowing 25,
        // whereas 9.0.1 as an AGP version would land on 24.
        assertEquals(25, decider.getMaxSupportedJavaVersion("9.3.0"))
        assertEquals(24, decider.getMaxSupportedJavaVersion("9.0.1"))
    }

}
