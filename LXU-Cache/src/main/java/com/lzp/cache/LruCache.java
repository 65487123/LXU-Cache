package com.lzp.cache;

import com.lzp.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description:基于LinkedHashMap LRU线程安全的缓存
 *
 * @author: Lu ZePing
 * @date: 2020/6/10 13:23
 */
public class LruCache<K, V> implements Cache<K, V> {

    //用volatile修饰对象，当这个对象里的没被volatile修饰的属性发生改变，其他线程都能通过这个对象看到变化
    private volatile Map<K, V> map;

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
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return this.size() > maxSize;
        }
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public synchronized V put(K key, V value) {
        return map.put(key, value);
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }

    @Override
    public synchronized V remove(K key) {
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
