package com.lzp.util;

import com.lzp.nettyserver.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Description:文件读取工具类
 *
 * @author: Lu ZePing
 * @date: 2020/6/10 13:23
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    public static String getProperty(String key) {
        Properties properties = new Properties();
        InputStream in = Server.class.getClassLoader().getResourceAsStream("config.properties");
        try {
            properties.load(in);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return properties.getProperty(key);
    }

    public static void generateFileIfNotExist(File file) {
        if (!file.exists()) {
            file.getParentFile().mkdir();
            file.mkdir();
        }
    }
}
