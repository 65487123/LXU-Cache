package com.lzp.cache;

import com.lzp.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description:基于LinkedHashMap LRU缓存淘汰策略缓存
 *
 * @author: Lu ZePing
 * @date: 2020/6/10 13:23
 */
public class LruCache<K, V> implements Cache<K, V> {

    private Map<K, V> map;

    private final int maxSize;

    public LruCache(int maxSize) {
        this.map = new AutoDeleteMap<>((int) (maxSize/0.75 +1), 0.75f, true);
        this.maxSize = maxSize;
    }

    private class AutoDeleteMap<K1, V1> extends LinkedHashMap<K1, V1> {
        AutoDeleteMap(int initialCapacity, float loadFactor, boolean accessOrder) {
            super(initialCapacity, loadFactor, accessOrder);
        }

        @Override
        public V1 put(K1 key, V1 value) {
            return super.put(key, value);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return this.size() > maxSize;
        }
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public V put(K key, V value) {
        return map.put(key, value);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public V remove(K key) {
        return map.remove(key);
    }

    @Override
    public int getMaxMemorySize() {
        return this.maxSize;
    }

    @Override
    public int getMemorySize() {
        return map.size();
    }
}
