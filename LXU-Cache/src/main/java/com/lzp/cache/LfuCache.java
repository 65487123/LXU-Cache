package com.lzp.cache;

import com.lzp.cache.Cache;

/**
 * Description:基于LFU淘汰的线程安全的缓存
 *
 * @author: Lu ZePing
 * @date: 2019/6/10 9:34
 */
public class LfuCache<K,V> implements Cache<K, V> {


    @Override
    public V get(K key) {
        return null;
    }

    @Override
    public V put(K key, V value) {
        return null;
    }

    @Override
    public void clear() {

    }

    public LfuCache(int maxSize) {
    }

    @Override
    public V remove(K key) {
        return null;
    }

    @Override
    public int getMaxMemorySize() {
        return 0;
    }

    @Override
    public int getMemorySize() {
        return 0;
    }
}
