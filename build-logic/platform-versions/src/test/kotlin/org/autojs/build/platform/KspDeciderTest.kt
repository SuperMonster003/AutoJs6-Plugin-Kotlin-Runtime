package org.autojs.build.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KspDeciderTest {

    private val dataSource = DataSource(localDataDir = null)

    private fun decider(gradleVersion: String = "9.3.0") = KspDecider(dataSource, gradleVersion)

    @Test
    fun `decides a KSP version for a known Kotlin release`() {
        val decision = decider().decideKspVersion("2.3.10")
        assertTrue(decision.version != null) { "expected a KSP version for Kotlin 2.3.10" }
        // KSP releases are named after the Kotlin release they target.
        assertTrue(decision.version!!.startsWith("2.3")) { "unexpected KSP version ${decision.version}" }
    }

    @Test
    fun `matches the nearest lower Kotlin release`() {
        val decision = decider().decideKspVersion("2.2.99")
        assertTrue(decision.version != null)
        assertTrue(decision.hintSuffix.contains(Identifier.NEAREST_LOWER_MATCHED))
    }

    @Test
    fun `leaves AGP alone when it already satisfies KSP`() {
        val kspVersion = decider().decideKspVersion("2.3.10").version
        val minimum = kspVersion?.let { decider().minimumAgpVersionForKsp(it) }
        if (minimum == null) return

        val decision = decider().refineAgpVersionForKsp(
            agpVersion = minimum,
            kspVersion = kspVersion,
            isUserSpecified = false,
        )
        assertEquals(minimum, decision.version)
        assertEquals("", decision.hintSuffix)
    }

    @Test
    fun `raises AGP when KSP needs a newer one`() {
        val kspVersion = decider().decideKspVersion("2.3.10").version
        val minimum = kspVersion?.let { decider().minimumAgpVersionForKsp(it) }
        if (minimum == null) return

        val decision = decider().refineAgpVersionForKsp(
            agpVersion = "8.2.2",
            kspVersion = kspVersion,
            isUserSpecified = false,
        )
        assertTrue(VersionComparator.compareVersionStrings(decision.version!!, minimum) >= 0) {
            "AGP ${decision.version} should be at least $minimum"
        }
        assertEquals(Identifier.UPGRADED_SUFFIX, decision.hintSuffix)
    }

    @Test
    fun `refuses to silently rewrite a user pinned AGP`() {
        val kspVersion = decider().decideKspVersion("2.3.10").version
        val minimum = kspVersion?.let { decider().minimumAgpVersionForKsp(it) }
        if (minimum == null) return

        val error = assertThrows<IllegalStateException> {
            decider().refineAgpVersionForKsp(
                agpVersion = "8.2.2",
                kspVersion = kspVersion,
                isUserSpecified = true,
            )
        }
        assertTrue(error.message!!.contains("incompatible with KSP"))
    }

    @Test
    fun `leaves AGP alone when no KSP version was decided`() {
        val decision = decider().refineAgpVersionForKsp("9.0.1", kspVersion = null, isUserSpecified = false)
        assertEquals("9.0.1", decision.version)
    }

    @Test
    fun `checks AGP against the running Gradle version`() {
        assertTrue(decider().canUseAgpWithCurrentGradle("9.0.1"))
        // AGP 9.3 needs Gradle 9.5, which the 9.3.0 line cannot provide.
        assertFalse(decider().canUseAgpWithCurrentGradle("9.3.0"))
        assertTrue(decider(gradleVersion = "9.5.0").canUseAgpWithCurrentGradle("9.3.0"))
    }

    @Test
    fun `picks the lowest usable AGP at or above a minimum`() {
        // A minimum from the 8.x line is still met by the oldest AGP that remains, since
        // support now starts at 9.0.
        assertEquals("9.0.1", decider().getAgpReleasedVersionAtLeast("8.9.0"))
        assertEquals("9.1.1", decider(gradleVersion = "9.4.0").getAgpReleasedVersionAtLeast("9.1.0"))
    }

}
