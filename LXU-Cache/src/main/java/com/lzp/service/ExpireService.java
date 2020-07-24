package com.lzp.service;

import com.lzp.protocol.CommandDTO;
import com.lzp.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Description:用来处理key超时过期相关事件的服务，独立一个线程
 *
 * key过期处理主要有三种办法：
 * 1、在下一次访问key时判断这个key是否设置了超时时间、是否已经过期，如果过期了，删除key，返回null
 * 2、收到给key设置超时时间的请求后就开一个线程，sleep超时时间的秒数后删除这个key。
 * 3、new一个map用来存放设置超时时间的key。独立起个线程，每个一段时间轮询这个map中的key是否已经超时如果超时了，就把key从缓存中删除
 *
 * 第一种方法影响读缓存效率，所以不考虑
 * 第二种办法可能会导致线程数量太多，也会影响性能。并且，一个key的超时时间只能设置一次，不能覆盖超时时间。
 * 所以这里选择用第三种方法来处理过期key。适量浪费单个cpu的资源问题也不是很大，用字符串常量池里的对象来当key，也不会造成额外内存占用过多
 *
 * @author: Lu ZePing
 * @date: 2020/7/14 11:07
 */
public class ExpireService {
    /**用来存放设置过期时间的key以及它的具体过期时刻*/
    private static Map<String, Long> keyTimeMap = new ConcurrentHashMap<>(16);

    /**处理过期key的线程*/
    private static final ExecutorService threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue(1), new ThreadFactoryImpl("expire handler"));

    private static final long pollingInterval;

    private static final Logger logger = LoggerFactory.getLogger(ExpireService.class);
    static {
        pollingInterval = Long.parseLong(FileUtil.getProperty("pollingInterval"));
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    //这里假设遍历map的时间是很快的，所以不用在判断每个key的时候重新计算当前时间，如果有漏网之鱼，只能在下一次轮询的时候揪出来删除了
                    Long now = Instant.now().toEpochMilli();
                    Iterator<Map.Entry<String, Long>> iterator = keyTimeMap.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Long> entry = iterator.next();
                        if (now > entry.getValue()) {
                            ConsMesServiWitOneQue.addMessage(new ConsMesServiWitOneQue.Message(CommandDTO.Command.newBuilder().setKey(entry.getKey()).setType("remove").build(),null),0);
                            iterator.remove();
                        }
                    }
                    try {
                        Thread.sleep(pollingInterval);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
    }

    public static void setKeyAndTime(String key, Long expire) {
        keyTimeMap.put(key, Instant.now().toEpochMilli() + expire);
    }
}