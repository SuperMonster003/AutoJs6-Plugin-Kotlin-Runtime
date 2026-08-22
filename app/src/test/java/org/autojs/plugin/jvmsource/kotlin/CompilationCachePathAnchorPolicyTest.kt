package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilationCachePathAnchorPolicyTest {
    @Test
    fun acceptsOnlyTheSameValidDirectoryIdentity() {
        assertTrue(CompilationCachePathAnchorPolicy.unchanged(1L, 2L, 1L, 2L))
        assertFalse(CompilationCachePathAnchorPolicy.unchanged(1L, 2L, 3L, 2L))
        assertFalse(CompilationCachePathAnchorPolicy.unchanged(1L, 2L, 1L, 3L))
        assertFalse(CompilationCachePathAnchorPolicy.unchanged(-1L, 2L, -1L, 2L))
        assertFalse(CompilationCachePathAnchorPolicy.unchanged(1L, 0L, 1L, 0L))
    }
}
