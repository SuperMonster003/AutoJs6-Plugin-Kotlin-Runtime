package org.autojs.plugin.jvmsource.kotlin.compat;

import org.jetbrains.kotlin.com.intellij.util.containers.ConcurrentLongObjectMap;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class AndroidConcurrentLongObjectMapTest {

    @Test
    public void preservesConcurrentLongObjectMapAtomicAndSnapshotSemantics() {
        AndroidConcurrentLongObjectMap<String> values = new AndroidConcurrentLongObjectMap<>();

        assertNull(values.put(7L, "first"));
        assertEquals("first", values.putIfAbsent(7L, "ignored"));
        assertEquals("first", values.get(7L));
        assertNull(values.putIfAbsent(9L, "second"));

        Map<Long, String> snapshot = new HashMap<>();
        for (ConcurrentLongObjectMap.LongEntry<String> entry : values.entries()) {
            snapshot.put(entry.getKey(), entry.getValue());
        }
        assertEquals(Map.of(7L, "first", 9L, "second"), snapshot);
        assertEquals("first", values.remove(7L));
        assertNull(values.get(7L));
    }
}
