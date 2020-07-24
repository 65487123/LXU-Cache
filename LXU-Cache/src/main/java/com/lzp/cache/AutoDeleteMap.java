package com.lzp.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description:实现了LRU淘汰策略的缓存
 *
 * @author: Lu ZePing
 * @date: 2020/6/10 13:23
 */
public class AutoDeleteMap<K, V> extends LinkedHashMap<K, V> implements Cache<K, V> {
    private final int maxSize;

    public AutoDeleteMap(int maxSize) {
        super((int) (maxSize / 0.75 + 1), 0.75f, true);
        this.maxSize = maxSize;
    }


    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return this.size() > maxSize;
    }


    @Override
    public int getMaxMemorySize() {
        return this.maxSize;
    }

    @Override
    public int getMemorySize() {
        return this.size();
    }
}
