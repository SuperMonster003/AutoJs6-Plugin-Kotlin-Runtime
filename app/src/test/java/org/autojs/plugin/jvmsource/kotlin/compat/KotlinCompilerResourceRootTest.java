package org.autojs.plugin.jvmsource.kotlin.compat;

import org.jetbrains.kotlin.utils.PathUtil;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KotlinCompilerResourceRootTest {

    @Test
    public void patchedPathUtilUsesThePortableCompilerResourceResolver() throws Exception {
        File expected = KotlinCompilerResourceRoot.resolve(PathUtil.class).getCanonicalFile();
        File actual = PathUtil.getResourcePathForClass(PathUtil.class).getCanonicalFile();

        assertTrue(expected.isFile() || expected.isDirectory());
        assertEquals(expected, actual);
    }
}
