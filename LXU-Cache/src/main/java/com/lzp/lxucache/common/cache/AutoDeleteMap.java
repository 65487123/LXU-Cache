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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description:实现了LRU淘汰策略的缓存
 *
 * @author: Lu ZePing
 * @date: 2019/6/10 13:23
 */
public class AutoDeleteMap<K, V> extends LinkedHashMap<K, V> implements Cache<K, V> {
    private final int maxSize;
    private static final long serialVersionUID = 1L;

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
