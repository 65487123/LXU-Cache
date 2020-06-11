package lzp;

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
        this.map = new LinkedHashMap<K, V>((int) (maxSize / 0.75) + 1, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public synchronized V put(K key, V value) {
        V v = map.put(key, value);
        if (map.size() > maxSize) {
            map.remove(map.keySet().iterator().next());
        }
        return v;
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
