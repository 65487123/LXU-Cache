package com.lzp.service;

import com.lzp.cache.Cache;
import com.lzp.cache.LfuCache;
import com.lzp.cache.LruCache;
import com.lzp.nettyserver.Server;
import com.lzp.protocol.CommandDTO;
import com.lzp.protocol.ResponseDTO;
import com.lzp.util.FileUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
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
    private static final Cache[] CACHES;

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private static final boolean IS_POWER_Of_TWO;
    private static ExecutorService threadPool;
    static {
        int maxSize = Integer.parseInt(FileUtil.getProperty("lruCacheMaxSize"));
        int cpuSum = Runtime.getRuntime().availableProcessors();
        threadPool = new ThreadPoolExecutor(2 * cpuSum, 2 * cpuSum, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1));
        IS_POWER_Of_TWO = (cpuSum & (cpuSum - 1)) == 0;
        QUEUES = new BlockingQueue[cpuSum];
        CACHES = new Cache[cpuSum];
        if ("LRU".equals(FileUtil.getProperty("strategy"))){
            for (int i = 0 ;i<cpuSum ;i++){
                CACHES[i] = new LruCache(maxSize);
                QUEUES[i] = new LinkedBlockingQueue<>(maxSize);
                final int finalI = i;
                threadPool.execute(() -> operCache(finalI));
            }
        }else {
            for (int i = 0 ;i<cpuSum ;i ++){
                CACHES[i] = new LfuCache(maxSize);
                QUEUES[i] = new LinkedBlockingQueue<>(maxSize);
                final int finalI = i;
                threadPool.execute(() -> operCache(finalI));
            }
        }
    }

    public static class Message{
        CommandDTO commandDTO;
        ChannelHandlerContext channelHandlerContext;

        public Message(CommandDTO commandDTO, ChannelHandlerContext channelHandlerContext) {
            this.commandDTO = commandDTO;
            this.channelHandlerContext = channelHandlerContext;
        }

        public CommandDTO getCommandDTO() {
            return commandDTO;
        }

        public ChannelHandlerContext getChannelHandlerContext() {
            return channelHandlerContext;
        }
    }

    private static void operCache(int index) {
        try {
            while (true) {
                Message message = QUEUES[index].take();
                CommandDTO commandDTO = message.commandDTO;
                switch (commandDTO.getType()) {
                    case "get":
                        message.channelHandlerContext.writeAndFlush(new ResponseDTO("get", CACHES[index].get(commandDTO.getKey())).toBytes());
                        break;
                    case "put":
                        message.channelHandlerContext.writeAndFlush(new ResponseDTO("put", CACHES[index].put(commandDTO.getKey(), commandDTO.getValue())).toBytes());
                        break;
                    case "remove":
                        message.channelHandlerContext.writeAndFlush(new ResponseDTO("remove", CACHES[index].remove(commandDTO.getKey())).toBytes());
                        break;
                    case "getMaxMemorySize":
                        message.channelHandlerContext.writeAndFlush(new ResponseDTO("getMaxMemorySize", CACHES[index].getMaxMemorySize()).toBytes());
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + commandDTO.getType());
                }
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void addMessage(Message message) {
        String h = String.valueOf(message.commandDTO.getKey().hashCode());
        int lastNum = Integer.parseInt(h.substring(h.length()-1));
        if (IS_POWER_Of_TWO){
            QUEUES[lastNum & (QUEUES.length-1)].offer(message);
        }else {
            QUEUES[lastNum % QUEUES.length-1].offer(message);
        }
    }
}
