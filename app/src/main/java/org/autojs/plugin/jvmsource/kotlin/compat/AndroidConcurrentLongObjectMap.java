package org.autojs.plugin.jvmsource.kotlin.compat;

import org.jetbrains.kotlin.com.intellij.util.containers.ConcurrentLongObjectMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Android replacement for the IntelliJ primitive-long map whose HotSpot Unsafe atomics are not
 * available on ART. The compiler uses the interface for progress-indicator bookkeeping; boxed
 * keys preserve its required atomic put/get/remove/put-if-absent semantics.
 */
public final class AndroidConcurrentLongObjectMap<V> implements ConcurrentLongObjectMap<V> {

    private final ConcurrentHashMap<Long, V> values = new ConcurrentHashMap<>();

    @Override
    public V put(long key, V value) {
        return values.put(key, value);
    }

    @Override
    public V get(long key) {
        return values.get(key);
    }

    @Override
    public V remove(long key) {
        return values.remove(key);
    }

    @Override
    public Iterable<LongEntry<V>> entries() {
        List<LongEntry<V>> snapshot = new ArrayList<>(values.size());
        for (Map.Entry<Long, V> entry : values.entrySet()) {
            snapshot.add(new Entry<>(entry.getKey(), entry.getValue()));
        }
        return snapshot;
    }

    @Override
    public V putIfAbsent(long key, V value) {
        return values.putIfAbsent(key, value);
    }

    private static final class Entry<V> implements LongEntry<V> {

        private final long key;
        private final V value;

        private Entry(long key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public long getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }
    }
}
