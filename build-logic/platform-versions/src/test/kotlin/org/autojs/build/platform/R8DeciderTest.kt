package org.autojs.build.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class R8DeciderTest {

    private val decider = R8Decider(DataSource(localDataDir = null))

    @Test
    fun `looks up the R8 requirement by the Kotlin major minor line`() {
        // The table is keyed by the Kotlin line, so patch versions and qualifiers are ignored.
        val fromRelease = decider.minimumR8VersionFor("2.2.21")
        assertEquals(fromRelease, decider.minimumR8VersionFor("2.2.0"))
        assertEquals(fromRelease, decider.minimumR8VersionFor("2.2.21-RC"))
    }

    @Test
    fun `reports no requirement for a Kotlin line the table does not know`() {
        // The table currently spans 1.3 to 2.4, so anything outside that range is unknown.
        assertNull(decider.minimumR8VersionFor("1.2.71"))
        assertNull(decider.minimumR8VersionFor("3.0.0"))
    }

    @Test
    fun `covers the Kotlin lines the AutoJs6 projects actually build with`() {
        assertEquals("8.13.19", decider.minimumR8VersionFor("2.3.10"))
        assertEquals("8.10.21", decider.minimumR8VersionFor("2.2.21"))
    }

    @Test
    fun `prefers the bundled R8 when AGP is on a newer line`() {
        assertFalse(decider.shouldUseExternalR8(agpVersion = "9.0.1", minimumR8Version = "8.13.19"))
    }

    @Test
    fun `requires an external R8 when AGP is on an older line`() {
        assertTrue(decider.shouldUseExternalR8(agpVersion = "8.7.3", minimumR8Version = "9.0.1"))
    }

    @Test
    fun `compares patch versions within the same line`() {
        assertTrue(decider.shouldUseExternalR8(agpVersion = "8.13.1", minimumR8Version = "8.13.19"))
        assertFalse(decider.shouldUseExternalR8(agpVersion = "8.13.19", minimumR8Version = "8.13.19"))
        assertFalse(decider.shouldUseExternalR8(agpVersion = "8.13.20", minimumR8Version = "8.13.19"))
    }

}
