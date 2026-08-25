package org.autojs.build.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionComparatorTest {

    private fun assertOrdered(smaller: String, greater: String) {
        assertTrue(VersionComparator.compareVersionStrings(smaller, greater) < 0) { "$smaller should be less than $greater" }
        assertTrue(VersionComparator.compareVersionStrings(greater, smaller) > 0) { "$greater should be greater than $smaller" }
    }

    @Test
    fun `compares plain numeric versions`() {
        assertOrdered("8.12.0", "9.0.1")
        assertOrdered("9.0.1", "9.1.1")
        assertOrdered("2025.2.2", "2026.1")
        assertOrdered("2026.1.2", "2026.2")
    }

    @Test
    fun `treats missing trailing segments as zero`() {
        assertEquals(0, VersionComparator.compareVersionStrings("9.0", "9.0.0"))
        assertEquals(0, VersionComparator.compareVersionStrings("2026.2", "2026.2.0"))
        assertOrdered("2026.2", "2026.2.1")
    }

    @Test
    fun `ranks qualifiers before plain releases`() {
        assertOrdered("9.0.1-alpha01", "9.0.1")
        assertOrdered("9.0.1-beta1", "9.0.1-rc1")
        assertOrdered("9.0.1-alpha01", "9.0.1-beta1")
        assertOrdered("9.0.1-canary1", "9.0.1-alpha01")
    }

    @Test
    fun `normalizes qualifier aliases`() {
        assertEquals(0, VersionComparator.compareVersionStrings("1.2.3-a1", "1.2.3-alpha1"))
        assertEquals(0, VersionComparator.compareVersionStrings("1.2.3-b2", "1.2.3-beta2"))
        assertEquals(0, VersionComparator.compareVersionStrings("1.2.3-cr1", "1.2.3-rc1"))
        assertEquals(0, VersionComparator.compareVersionStrings("1.2.3-m2", "1.2.3-milestone2"))
        assertEquals(0, VersionComparator.compareVersionStrings("1.2.3-pre1", "1.2.3-preview1"))
    }

    @Test
    fun `ranks stable synonyms alongside plain releases`() {
        // Synonyms share priority 10, so they all outrank pre-release qualifiers.
        assertOrdered("1.2.3-rc9", "1.2.3-stable")
        assertOrdered("1.2.3-rc9", "1.2.3-ga")
        // Named synonyms carry an implicit qualifier number of 1, whereas a bare
        // release carries 0, so "1.2.3" sorts just below "1.2.3-stable". Downstream
        // maps only ever hold bare releases, so this quirk never changes a decision.
        assertOrdered("1.2.3", "1.2.3-stable")
        assertEquals(0, VersionComparator.compareVersionStrings("1.2.3-ga", "1.2.3-final"))
    }

    @Test
    fun `orders qualifier numbers within the same qualifier`() {
        assertOrdered("1.2.3-rc1", "1.2.3-rc2")
        assertOrdered("1.2.3-alpha01", "1.2.3-alpha02")
    }

    @Test
    fun `parses assorted qualifier separators`() {
        val (numbers, suffix) = VersionComparator.toVersionParts("1.2.3-rc1")
        assertEquals(listOf(1, 2, 3), numbers)
        assertEquals("rc" to 1, suffix)

        assertEquals("rc" to 1, VersionComparator.toVersionParts("1.2.3 RC 1").second)
        assertEquals("alpha" to 1, VersionComparator.toVersionParts("1.2.3-Alpha").second)
        assertEquals("preview" to 2, VersionComparator.toVersionParts("1.2.3-preview 2").second)
        assertEquals("milestone" to 2, VersionComparator.toVersionParts("1.2.3+m2").second)
    }

    @Test
    fun `treats wildcard segments as zero`() {
        // Keys such as "2.3.Z" appear in ksp-releases.properties, standing for any patch.
        assertEquals(listOf(2, 3, 0), VersionComparator.toVersionParts("2.3.Z").first)
        assertEquals(listOf(2, 3, 0), VersionComparator.toVersionParts("2.3.x").first)
        assertEquals(listOf(2, 3, 0), VersionComparator.toVersionParts("2.3.*").first)
        assertEquals(listOf(2, 3, 0), VersionComparator.toVersionParts("2.3.?").first)
        assertEquals(0, VersionComparator.compareVersionStrings("2.3.Z", "2.3.0"))
        assertOrdered("2.3.Z", "2.3.1")
    }

    @Test
    fun `sorts descending with the reversed comparator`() {
        val sorted = listOf("8.12.0", "9.1.1", "9.0.1").sortedWith(VersionComparator::compareVersionStringsDesc)
        assertEquals(listOf("9.1.1", "9.0.1", "8.12.0"), sorted)
    }

    @Test
    fun `rejects malformed numeric segments`() {
        // "x" alone is a wildcard, but a longer alphabetic run is simply invalid.
        assertThrows<IllegalArgumentException> { VersionComparator.toVersionParts("1.xy.3") }
        assertThrows<IllegalArgumentException> { VersionComparator.toVersionParts("1.beta.3") }
        // The version/qualifier split only recognizes whitespace, "+" and "-", so an
        // underscore stays glued to the numeric part and fails to parse. Kept as an
        // explicit expectation: no real AGP / IDE version string uses that form.
        assertThrows<IllegalArgumentException> { VersionComparator.toVersionParts("1.2.3_preview-2") }
    }

    @Test
    fun `sorts version names descending`() {
        with(VersionComparator) {
            val sorted = listOf("8.12.0", "9.1.1", "9.0.1", "9.0.1-rc1").sortByVersionName(isDescend = true)
            assertEquals(listOf("9.1.1", "9.0.1", "9.0.1-rc1", "8.12.0"), sorted)
        }
    }

}
