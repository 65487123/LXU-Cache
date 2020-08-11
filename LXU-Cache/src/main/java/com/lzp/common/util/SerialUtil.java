package com.lzp.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Description:序列化工具类，针对自己的协议制定的序列化规则，达到效率最高，网络传输最少的目的。
 * @author: Lu ZePing
 * @date: 2019/7/10 9:46
 */
public class SerialUtil {
    private static final Logger logger = LoggerFactory.getLogger(SerialUtil.class);

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
