 /* Copyright zeping lu
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */

package com.lzp.lxucache.common.cache;

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
