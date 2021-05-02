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

package com.lzp.lxucache.common.util;

/**
 * Description:工具类
 *
 * @author: Lu ZePing
 * @date: 2020/8/10 11:31
 */
public class HashUtil {
    /**
     * Description ：把字符串转每个字符相加
     *
     * @param
     * @Return
     **/
    public static int sumChar(String key) {
        int sum = 0;
        char[] chars = key.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            sum += chars[i];
        }
        return sum;
    }


    /**
     * Description ：
     *
     * @param
     * @Return
     **/
    public static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n + 1;
    }
}
