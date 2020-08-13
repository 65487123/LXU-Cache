package com.lzp.common.util;

import com.lzp.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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


    public static void setProperty(String key,String value) {
        properties.setProperty(key,value);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream("src/main/resources/config.properties");
            //将Properties中的属性列表（键和元素对）写入输出流
            properties.store(fos, "");
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                logger.error(e.getMessage(),e);
            }
        }
    }

    public static void generateFileIfNotExist(File file) {
        if (!file.exists()) {
            file.getParentFile().mkdir();
            file.mkdir();
        }
    }
}
