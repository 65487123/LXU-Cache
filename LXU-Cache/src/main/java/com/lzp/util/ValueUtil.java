package com.lzp.util;

import java.util.*;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/7/10 9:46
 */
public class ValueUtil {

    /**
     * Description ：把string转为map
     * @param
     * @Return
     **/
    public static Map<String, String> stringToMap(String json) {
        String[] strings = json.split("⚫");
        Map<String, String> map = new HashMap(16);
        for (int i = 0; i < strings.length; i++) {
            String[] keyValue = strings[i].split(":");
            StringBuilder key = new StringBuilder(keyValue[0]);
            StringBuilder value = new StringBuilder(keyValue[1]);
            map.put(key.toString(),value.toString());
        }
        return map;
    }


    /**
     * Description ：把collection变为字符串
     *
     * @param
     * @Return
     **/
    public static String collectionToString(Collection<String> strings) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : strings) {
            stringBuilder.append(string).append("⚫");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }
    /**
     * Description ：把字符串转为List
     *
     * @param
     * @Return
     **/
    public static List<String> stringToList(String listString) {
        List<String> list = new ArrayList<>();
        for (String string : listString.split("⚫")) {
            list.add(string);
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
        for (String string : listString.split("⚫")) {
            set.add(string);
        }
        return set;
    }
}
