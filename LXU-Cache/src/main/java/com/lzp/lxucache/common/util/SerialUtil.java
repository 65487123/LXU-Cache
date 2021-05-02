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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Description:序列化工具类，针对自己的协议制定的序列化规则，达到效率最高，网络传输最少的目的。
 * @author: Lu ZePing
 * @date: 2019/7/10 9:46
 */
public class SerialUtil {
    private static final Logger logger = LoggerFactory.getLogger(SerialUtil.class);


    public static byte[] toByteArray(String hexString) {
        if (StringUtils.isEmpty(hexString)) {
            return new byte[0];
        }

        hexString = hexString.toLowerCase();
        final byte[] byteArray = new byte[hexString.length() / 2];
        int k = 0;
        for (int i = 0; i < byteArray.length; i++) {
            //因为是16进制，最多只会占用4位，转换成字节需要两个16进制的字符，高位在先
            byte high = (byte) (Character.digit(hexString.charAt(k), 16) & 0xff);
            byte low = (byte) (Character.digit(hexString.charAt(k + 1), 16) & 0xff);
            byteArray[i] = (byte) (high << 4 | low);
            k += 2;
        }
        return byteArray;
    }

    /**
     * 字节数组转成16进制表示格式的字符串
     *
     * @param byteArray 需要转换的字节数组
     * @return 16进制表示格式的字符串
     **/
    public static String toHexString(byte[] byteArray) {
        if (byteArray == null || byteArray.length < 1) {
            return "";
        }
        final StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < byteArray.length; i++) {
            if ((byteArray[i] & 0xff) < 0x10) {
                hexString.append("0");
            }
            hexString.append(Integer.toHexString(0xFF & byteArray[i]));
        }
        return hexString.toString().toLowerCase();
    }



    /**
     * Description ：把string转为map

     **/
    public static Map<String, String> stringToMap(String string) {
        String[] strings = string.split("È");
        Map<String, String> map = new HashMap(16);
        for (int i = 0; i < strings.length; i++) {
            String[] keyValue = strings[i].split("©");
            map.put(keyValue[0].intern(),keyValue[1].intern());
        }
        return map;
    }


    /**
     * Description ：把collection变为字符串
     *
     **/
    public static String collectionToString(Collection<String> strings) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : strings) {
            stringBuilder.append(string).append("È");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }
    /**
     * Description ：把字符串转为List

     **/
    public static List<String> stringToList(String listString) {
        List<String> list = new ArrayList<>();
        for (String string : listString.split("È")) {
            list.add(string.intern());
        }
        return list;
    }
    /**
     * Description ：把字符串转为Set
     *
     * @param
     * @Return
     **/
    public static Set<String> stringToSet(String listString) {
        Set<String> set = new HashSet<>();
        for (String string : listString.split("È")) {
            set.add(string);
        }
        return set;
    }


}
