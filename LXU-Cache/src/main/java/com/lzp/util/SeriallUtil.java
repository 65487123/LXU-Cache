package com.lzp.util;

import com.lzp.cache.AutoDeleteMap;
import com.lzp.cache.Cache;
import sun.awt.SunHints;
import sun.misc.LRUCache;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Description:序列化工具类，针对自己的协议制定的序列化规则，达到效率最高，网络传输最少的目的。
 * 从代码可以看出，序列化以及反序列化用到了一些特殊字符作为分隔。 这就表明这个缓存的key和value
 * 不应该含有以下四个特殊字符：⚫  ©  ❤  →
 * @author: Lu ZePing
 * @date: 2020/7/10 9:46
 */
public class SeriallUtil {
    /**
     * Description ：把string转为map

     **/
    public static Map<String, String> stringToMap(String json) {
        String[] strings = json.split("⚫");
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
            stringBuilder.append(string).append("⚫");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }
    /**
     * Description ：把字符串转为List

     **/
    public static List<String> stringToList(String listString) {
        List<String> list = new ArrayList<>();
        for (String string : listString.split("⚫")) {
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
        for (String string : listString.split("⚫")) {
            set.add(string);
        }
        return set;
    }

    /**
     * Description ：
     * 键❤值类型→具体值☁键❤值类型→具体值
     * 具体值如果是每个元素之间用⚫分割，如果元素是map的node，则node的keyvalue之间用©
     *例子
     * map类型
     * 123❤3→1©3⚫3©4☁343❤3→1©3⚫3©4
     * @param
     * @Return
     **/
    public static String CacheToString(AutoDeleteMap<String,Object> map)  {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String,Object> entry:map.entrySet()){
            stringBuilder.append(entry.getKey()).append("❤");
            Object value = entry.getValue();
            if (value instanceof String){
                stringBuilder.append("0→").append((String)value).append("☁");
            }else if (value instanceof List){
                stringBuilder.append("1→");
                for (String str :(List<String>)value) {
                stringBuilder.append(str).append("⚫");
                }
            }else if (value instanceof Set){
                stringBuilder.append("2→");
                for (String str :(Set<String>)value) {
                    stringBuilder.append(str).append("⚫");
                }
                stringBuilder.deleteCharAt(stringBuilder.length()-1).append("☁");
            }else if (value instanceof Map){
                stringBuilder.append("3→");
                for (Map.Entry<String,String> entry1:((Map<String,String>) value).entrySet()) {
                    stringBuilder.append(entry1.getKey()).append("©").append(entry1.getValue()).append("⚫");
                }
                stringBuilder.deleteCharAt(stringBuilder.length()-1).append("☁");
            }else {

            }
        }
        stringBuilder.deleteCharAt(stringBuilder.length()-1);
        return stringBuilder.toString();
    }
    /**
     * Description ：
     * 键❤值类型→具体值☁键❤值类型→具体值
     * 具体值如果是每个元素之间用⚫分割，如果元素是map的node，则node的keyvalue之间用©
     *例子
     * map类型
     * 123❤3→1©3⚫3©4☁343❤3→1©3⚫3©4

     **/
    /*public static String CacheToString(AutoDeleteMap<String,Object> map,String fileName) throws IOException {
        FileWriter fileWriter = new FileWriter(fileName);
        for (Map.Entry<String,Object> entry:map.entrySet()){
            fileWriter.write(entry.getKey());
            fileWriter.write("❤");
            Object value = entry.getValue();
            if (value instanceof String){
                fileWriter.write("0→");
                fileWriter.write((String)value);
                fileWriter.write("☁");
            }else if (value instanceof List){
                fileWriter.write("1→");
                for (String str :(List<String>)value) {
                    fileWriter.write(str);
                    fileWriter.write("⚫");
                }
            }else if (value instanceof Set){
                stringBuilder.append("2→");
                for (String str :(Set<String>)value) {
                    stringBuilder.append(str).append("⚫");
                }
                stringBuilder.deleteCharAt(stringBuilder.length()-1).append("☁");
            }else if (value instanceof Map){
                stringBuilder.append("3→");
                for (Map.Entry<String,String> entry1:((Map<String,String>) value).entrySet()) {
                    stringBuilder.append(entry.getKey()).append("©").append(entry.getValue()).append("⚫");
                }
                stringBuilder.deleteCharAt(stringBuilder.length()-1).append("☁");
            }else {

            }
        }
        stringBuilder.deleteCharAt(stringBuilder.length()-1);
        return stringBuilder.toString();
    }*/
    public static AutoDeleteMap StringToLruCache(String str){
        AutoDeleteMap<String,Object> cache = new AutoDeleteMap<>(10000);
        for (String string:str.split("☁")){
            String[] keyAndValue = string.split("❤");
            String key = keyAndValue[0];
            String[] typeAndValue = keyAndValue[1].split("→");
            switch (typeAndValue[0]){
                case "0":{
                    cache.put(key,typeAndValue[1]);
                    break;
                }
                case "1":{
                    List<String> list = Arrays.asList( typeAndValue[1].split("⚫"));
                    cache.put(key,list);
                    break;
                }
                case "2":{
                    Set<String> set =new HashSet<>(Arrays.asList( typeAndValue[1].split("⚫")));
                    cache.put(key,set);
                    break;
                }
                case "3":{
                    Map map = new HashMap(16);
                    for (String s :typeAndValue[1].split("⚫")){
                        String[] kv = s.split("©");
                        map.put(kv[0],kv[1]);
                    }
                    cache.put(key,map);
                    break;
                }
            }
        }
        return cache;
    }

    public static Cache StringToLuCache(String str){
        return null;
    }
}
