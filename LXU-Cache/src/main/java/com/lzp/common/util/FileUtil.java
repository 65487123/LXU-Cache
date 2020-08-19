package com.lzp.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

/**
 * Description:文件读取工具类
 *
 * @author: Lu ZePing
 * @date: 2019/6/10 13:23
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    private static Properties properties = new Properties();

    static {
        InputStream in = FileUtil.class.getClassLoader().getResourceAsStream("config.properties");
        try {
            properties.load(in);
        } catch (IOException e) {
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }


    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
        FileOutputStream fos = null;
        try {
            String path = FileUtil.class.getResource("/").getPath();
            if (path.startsWith("file:")) {
                path = path.split("file:")[1];
            }
            fos = new FileOutputStream("config.properties");
            //将Properties中的属性列表（键和元素对）写入输出流
            properties.store(fos, "");
            String cmd = "jar uf lxucache-server-1.0-SNAPSHOT.jar config.properties";
            Runtime.getRuntime().exec(new String[]{"/bin/sh","-c",cmd}).waitFor();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static void generateFileIfNotExist(File file) {
        if (!file.exists()) {
            file.getParentFile().mkdir();
            file.mkdir();
        }
    }

    public static void closeResource(Closeable ... closeables){
        for (int i=0;i<closeables.length;i++){
            if (closeables[i]!=null){
                try {
                    closeables[i].close();
                } catch (IOException e) {
                    logger.error(e.getMessage(),e);
                }
            }
        }
    }
}
