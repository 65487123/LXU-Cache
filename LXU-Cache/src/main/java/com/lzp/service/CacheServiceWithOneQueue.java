package com.lzp.service;

import com.lzp.cache.Cache;
import com.lzp.cache.LfuCache;
import com.lzp.cache.LruCache;
import com.lzp.protocol.CommandDTO;
import com.lzp.protocol.ResponseDTO;
import com.lzp.util.FileUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Description:只有一个消息队列的缓存服务
 *
 * @author: Lu ZePing
 * @date: 2020/7/1 18:13
 */
public class CacheServiceWithOneQueue {
    private static final BlockingQueue<CacheServiceWithOneQueue.Message> QUEUE;

    private static final Cache<String,String> CACHE;

    private static final Logger logger = LoggerFactory.getLogger(CacheServiceWithOneQueue.class);

    private static ExecutorService threadPool;

    static {
        int maxSize = Integer.parseInt(FileUtil.getProperty("lruCacheMaxSize"));
        int cpuSum = Runtime.getRuntime().availableProcessors();
        threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), new ThreadFactoryImpl("operCache"));
        if ("LRU".equals(FileUtil.getProperty("strategy"))) {
                CACHE = new LruCache<String,String>(maxSize);
                QUEUE = new ArrayBlockingQueue<>(maxSize);
                threadPool.execute(() -> operCache());
        } else {
                CACHE = new LfuCache(maxSize);
                QUEUE = new ArrayBlockingQueue<>(maxSize);
                threadPool.execute(() -> operCache());

        }
    }

    public static class Message{
        CommandDTO.Command command;
        ChannelHandlerContext channelHandlerContext;

        public Message(CommandDTO.Command command, ChannelHandlerContext channelHandlerContext) {
            this.command = command;
            this.channelHandlerContext = channelHandlerContext;
        }

        public CommandDTO.Command getCommand() {
            return command;
        }

        public ChannelHandlerContext getChannelHandlerContext() {
            return channelHandlerContext;
        }
    }

    private static void operCache() {
        try {
            while (true) {
                CacheServiceWithOneQueue.Message message = QUEUE.take();
                switch (message.command.getType()) {
                    case "get": {
                        Object retern = CACHE.get(message.command.getKey());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("get").setResult(result).build());
                        break;
                    }
                    case "put": {
                        Object retern = CACHE.put(message.command.getKey(), message.command.getValue());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("put").setResult(result).build());
                        break;
                    }
                    case "remove": {
                        Object retern = CACHE.remove(message.command.getKey());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("remove").setResult(result).build());
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + message.command.getType());
                }
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void addMessage(CacheServiceWithOneQueue.Message message) {
        try {
            QUEUE.put(message);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(),e);
        }
    }

    private static int sumChar(String key) {
        int sum = 0;
        char[] chars = key.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            sum += chars[i];
        }
        return sum;
    }
}
