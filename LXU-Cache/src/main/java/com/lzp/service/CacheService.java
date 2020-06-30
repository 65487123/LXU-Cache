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
 * Description:缓存服务
 *
 * @author: Lu ZePing
 * @date: 2020/6/10 13:23
 */
public class CacheService {
    //逻辑处理器相同数量的消息队列
    private static final BlockingQueue<Message>[] QUEUES;
    //逻辑处理器相同数量的缓存
    private static final Cache<String,String>[] CACHES;

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private static final boolean IS_POWER_Of_TWO;
    private static ExecutorService threadPool;

    static {
        int maxSize = Integer.parseInt(FileUtil.getProperty("lruCacheMaxSize"));
        int cpuSum = Runtime.getRuntime().availableProcessors();
        threadPool = new ThreadPoolExecutor(cpuSum, cpuSum, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), new ThreadFactoryImpl("operCache", 10));
        IS_POWER_Of_TWO = (cpuSum & (cpuSum - 1)) == 0;
        QUEUES = new BlockingQueue[cpuSum];
        CACHES = new Cache[cpuSum];
        if ("LRU".equals(FileUtil.getProperty("strategy"))) {
            for (int i = 0; i < cpuSum; i++) {
                CACHES[i] = new LruCache<String,String>(maxSize);
                QUEUES[i] = new LinkedBlockingQueue<>(maxSize);
                final int finalI = i;
                threadPool.execute(() -> operCache(finalI));
            }
        } else {
            for (int i = 0; i < cpuSum; i++) {
                CACHES[i] = new LfuCache(maxSize);
                QUEUES[i] = new LinkedBlockingQueue<>(maxSize);
                final int finalI = i;
                threadPool.execute(() -> operCache(finalI));
            }
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

    private static void operCache(int index) {
        try {
            while (true) {
                Message message = QUEUES[index].take();
                switch (message.command.getType()) {
                    case "get": {
                        Object retern = CACHES[index].get(message.command.getKey());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("get").setResult(result).build());
                        break;
                    }
                    case "put": {
                        Object retern = CACHES[index].put(message.command.getKey(), message.command.getValue());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("put").setResult(result).build());
                        break;
                    }
                    case "remove": {
                        Object retern = CACHES[index].remove(message.command.getKey());
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

    public static void addMessage(Message message) {
        String key = String.valueOf(message.command.getKey());
        int charSum = sumChar(key);
        if (IS_POWER_Of_TWO){
            QUEUES[charSum & (QUEUES.length-1)].offer(message);
        }else {
            QUEUES[charSum % QUEUES.length-1].offer(message);
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
