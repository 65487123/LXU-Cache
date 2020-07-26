package com.lzp.service;

import com.lzp.cache.AutoDeleteMap;
import com.lzp.cache.Cache;
import com.lzp.cache.LfuCache;
import com.lzp.datastructure.queue.BlockingQueueAdapter;
import com.lzp.datastructure.queue.NoLockBlockingQueue;
import com.lzp.protocol.CommandDTO;
import com.lzp.protocol.ResponseDTO;
import com.lzp.util.FileUtil;
import com.lzp.util.SeriallUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Description:有多个消息队列的缓存服务,每个消息队列对应一个消费消息的线程
 *
 * @author: Lu ZePing
 * @date: 2020/6/10 13:23
 */
public class ConsumeMessageService {
    /**逻辑处理器相同数量的消息队列*/
    private static final BlockingQueueAdapter<Message>[] QUEUES;
    /**逻辑处理器相同数量的缓存*/
    private static final Cache<String,Object>[] CACHES;

    private static final Logger logger = LoggerFactory.getLogger(ConsumeMessageService.class);

    private static final boolean IS_POWER_Of_TWO;

    static {
        int maxSize = Integer.parseInt(FileUtil.getProperty("lruCacheMaxSize"));
        int cpuSum = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = new ThreadPoolExecutor(cpuSum, cpuSum, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), new ThreadFactoryImpl("operCache"));
        IS_POWER_Of_TWO = (cpuSum & (cpuSum - 1)) == 0;
        QUEUES = new BlockingQueueAdapter[cpuSum];
        CACHES = new Cache[cpuSum];
        if ("LRU".equals(FileUtil.getProperty("strategy"))) {
            for (int i = 0; i < cpuSum; i++) {
                CACHES[i] = new AutoDeleteMap<>(maxSize);
                QUEUES[i] = new NoLockBlockingQueue<>(maxSize,cpuSum);
                final int finalI = i;
                threadPool.execute(() -> operCache(finalI));
            }
        } else {
            for (int i = 0; i < cpuSum; i++) {
                CACHES[i] = new LfuCache(maxSize);
                QUEUES[i] = new NoLockBlockingQueue<>(maxSize,cpuSum);
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
                    case "incr": {
                        String key = message.command.getKey();
                        String afterValue;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) CACHES[index].get(message.command.getKey())) + 1);
                            CACHES[index].put(key, afterValue);
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("incr").setResult("e").build());
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("incr").setResult(afterValue).build());
                        break;
                    }
                    case "decr": {
                        String key = message.command.getKey();
                        String afterValue ;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) CACHES[index].get(message.command.getKey())) - 1);
                            CACHES[index].put(key, afterValue);
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("decr").setResult("e").build());
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("decr").setResult(afterValue).build());
                        break;
                    }
                    case "hput": {
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHES[index].get(key)) !=null && !(value instanceof Map)){
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hput").setResult("e").build());
                            break;
                        }
                        Map<String,String> values = SeriallUtil.stringToMap(message.command.getValue());
                        CACHES[index].put(key,values);
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hput").build());
                        break;
                    }
                    case "hmerge": {
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHES[index].get(key)) == null) {
                            Map<String, String> values = SeriallUtil.stringToMap(message.command.getValue());
                            CACHES[index].put(key, values);
                        } else if (!(value instanceof Map)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hmerge").setResult("e").build());
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            Map<String, String> values = SeriallUtil.stringToMap(message.command.getValue());
                            for (Map.Entry<String, String> entry : values.entrySet()) {
                                mapValue.put(entry.getKey(), entry.getValue());
                            }
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hmerge").build());
                        break;
                    }
                    case "lpush": {
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHES[index].get(key)) == null) {
                            //不values.addAll(Arrays.asList(message.command.getValue().split(","))) 这样写的原因是他底层也是要addAll的，没区别
                            //而且还多了一步new java.util.Arrays.ArrayList()的操作。虽然jvm在编译的时候可能就会优化成和我写的一样，但最终结果都一样，这样写直观一点。下面同样
                            CACHES[index].put(key, SeriallUtil.stringToList(message.command.getValue()));
                        } else if (!(value instanceof List)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("lpush").setResult("e").build());
                            break;
                        } else {
                            List<String> listValue = (List<String>) value;
                            listValue.addAll(Arrays.asList(message.command.getValue().split(",")));
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("lpush").build());
                        break;
                    }
                    case "sadd": {
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHES[index].get(key)) == null) {
                            CACHES[index].put(key, SeriallUtil.stringToSet(message.command.getValue()));
                        } else if (!(value instanceof List)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("zadd").setResult("e").build());
                            break;
                        } else {
                            Set<String> setValue = (Set<String>) value;
                            setValue.addAll(Arrays.asList(message.command.getValue().split(",")));
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("lpush").build());
                        break;
                    }
                    case "zadd": {
                        CACHES[index].remove(message.command.getKey());
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("remove").build());
                        break;
                    }
                    case "hget": {
                        try {
                            Map<String, String> values = (Map<String, String>) CACHES[index].get(message.command.getKey());
                            if (values == null) {
                                message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hget").setResult("null").build());
                            } else {
                                String result;
                                message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hget").setResult((result = values.get(message.command.getValue())) == null ? "null" : result).build());
                            }
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hget").setResult("e").build());
                        }
                        break;
                    }
                    case "getList": {
                        try {
                            List<String> values = (List<String>) CACHES[index].get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getList").setResult(values == null ? "null" : SeriallUtil.collectionToString(values)).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getList").setResult("e").build());
                        }
                        break;
                    }
                    case "getSet": {
                        try {
                            Set<String> values = (Set<String>) CACHES[index].get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getSet").setResult(values == null ? "null" : SeriallUtil.collectionToString(values)).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getSet").setResult("e").build());
                        }
                        break;
                    }
                    case "scontain": {
                        try {
                            Set<String> values = (Set<String>) CACHES[index].get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("scontain").setResult(String.valueOf(values.contains(message.command.getValue()))).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("scontain").setResult("e").build());
                        }
                        break;
                    }
                    case "expire": {
                        String key = message.command.getKey().intern();
                        if (CACHES[index].get(key) == null) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("expire").setResult("0").build());
                            break;
                        } else {
                            ExpireService.setKeyAndTime(key, Long.parseLong(message.command.getValue()) * 1000);
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("expire").setResult("1").build());
                            break;
                        }
                    }
                    case "remove": {
                        CACHES[index].remove(message.command.getKey());
                        if (message.channelHandlerContext != null) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("remove").build());
                        }
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
    /**
     * Description ：先根据key确定要放入的队列，再根据线程id确定队列中要存入的块
     *
     * @param
     * @Return
     **/
    public static void addMessage(Message message,int threadId) {
        String key = String.valueOf(message.command.getKey());
        int charSum = sumChar(key);
        if (IS_POWER_Of_TWO){
            try {
                QUEUES[charSum & (QUEUES.length-1)].put(message,threadId);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(),e);
            }
        }else {
            try {
                QUEUES[charSum % QUEUES.length-1].put(message,threadId);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(),e);
            }
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
