package com.lzp.singlemachine.service;

import com.lzp.common.cache.AutoDeleteMap;
import com.lzp.common.cache.Cache;
import com.lzp.common.cache.LfuCache;
import com.lzp.common.datastructure.queue.NoLockBlockingQueue;
import com.lzp.common.datastructure.set.Zset;
import com.lzp.common.protocol.CommandDTO;
import com.lzp.common.protocol.ResponseDTO;
import com.lzp.common.service.ExpireService;
import com.lzp.common.service.PersistenceService;
import com.lzp.common.service.ThreadFactoryImpl;
import com.lzp.common.util.FileUtil;
import com.lzp.common.util.HashUtil;
import com.lzp.common.util.SerialUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Description:有一个消息队列的缓存服务，对应一个消费消息的线程。
 *
 * @author: Lu ZePing
 * @date: 2019/7/1 18:13
 */
public class ConsMesService {
    private static final NoLockBlockingQueue<Message> QUEUE;

    private static final Cache<String, Object> CACHE;

    private static final Logger logger = LoggerFactory.getLogger(ConsMesService.class);

    private final static int SNAPSHOT_BATCH_COUNT_D1;

    private static int journalNum = 0;

    public static final int THREAD_NUM;

    static {
        int approHalfCpuCore;
        THREAD_NUM = (approHalfCpuCore = HashUtil.tableSizeFor(Runtime.getRuntime().availableProcessors()) / 2) < 1 ? 1 : approHalfCpuCore;
        int maxSize = Integer.parseInt(FileUtil.getProperty("lruCacheMaxSize"));
        SNAPSHOT_BATCH_COUNT_D1 = Integer.parseInt(FileUtil.getProperty("snapshot-batch-count")) - 1;
        ExecutorService threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), new ThreadFactoryImpl("operCache"));
        //如果有持久化文件就恢复数据，没有就初始化缓存
        if ("LRU".equals(FileUtil.getProperty("strategy"))) {
            File file = new File("./persistence/corecache/snapshot.ser");
            if (!file.exists()) {
                CACHE = new AutoDeleteMap<>(maxSize);
            } else {
                ObjectInputStream objectInputStream = null;
                try {
                    objectInputStream = new ObjectInputStream(new FileInputStream(file));
                    CACHE = (AutoDeleteMap<String, Object>) objectInputStream.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException();
                } catch (ClassCastException e) {
                    logger.error("持久化文件的缓存淘汰策略和配置文件不一致");
                    throw e;
                } finally {
                    if (objectInputStream != null) {
                        try {
                            objectInputStream.close();
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
                BufferedReader bufferedReader = null;
                try {
                    bufferedReader = new BufferedReader(new FileReader(new File("./persistence/corecache/journal.txt")));
                    String cmd;
                    bufferedReader.readLine();
                    while ((cmd = bufferedReader.readLine()) != null) {
                        restoreData(cmd.split("ÈÈ"));
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException();
                } finally {
                    if (bufferedReader != null) {
                        try {
                            bufferedReader.close();
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }

            }
        } else {
            CACHE = new LfuCache(maxSize);
        }
        QUEUE = new NoLockBlockingQueue<>(Integer.parseInt(FileUtil.getProperty("queueSize")), THREAD_NUM);
        threadPool.execute(() -> operCache());
        //清空持久化文件，生成一次快照
        PersistenceService.generateSnapshot(CACHE);
    }

    public static class Message{
        CommandDTO.Command command;
        ChannelHandlerContext channelHandlerContext;

        public Message(CommandDTO.Command command, ChannelHandlerContext channelHandlerContext) {
            this.command = command;
            this.channelHandlerContext = channelHandlerContext;
        }

    }
    private static void restoreData(String[] strings){
        switch (strings[0]){
            case "put": {
                CACHE.put(strings[1], strings[2]);
                break;
            }
            case "incr": {
                String afterValue;
                try {
                    afterValue = String.valueOf(Integer.parseInt((String) CACHE.get(strings[1])) + 1);
                    CACHE.put(strings[1], afterValue);
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case "decr": {
                String afterValue ;
                try {
                    afterValue = String.valueOf(Integer.parseInt((String) CACHE.get(strings[1])) - 1);
                    CACHE.put(strings[1], afterValue);
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case "hput": {
                Object value;
                if ((value = CACHE.get(strings[1])) !=null && !(value instanceof Map)){
                    break;
                }
                Map<String,String> values = SerialUtil.stringToMap(strings[2]);
                CACHE.put(strings[1],values);
                break;
            }
            case "hmerge": {
                Object value;
                if ((value = CACHE.get(strings[1])) == null) {
                    Map<String, String> values = SerialUtil.stringToMap(strings[2]);
                    CACHE.put(strings[1], values);
                } else if (!(value instanceof Map)) {
                    break;
                } else {
                    Map<String, String> mapValue = (Map<String, String>) value;
                    Map<String, String> values = SerialUtil.stringToMap(strings[2]);
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        mapValue.put(entry.getKey(), entry.getValue());
                    }
                }
                break;
            }
            case "lpush": {
                Object value;
                if ((value = CACHE.get(strings[1])) == null) {
                    //不values.addAll(Arrays.asList(message.command.getValue().split(","))) 这样写的原因是他底层也是要addAll的，没区别
                    //而且还多了一步new java.util.Arrays.ArrayList()的操作。虽然jvm在编译的时候可能就会优化成和我写的一样，但最终结果都一样，这样写直观一点。下面同样
                    CACHE.put(strings[1], SerialUtil.stringToList(strings[2]));
                } else if (!(value instanceof List)) {
                    break;
                } else {
                    List<String> listValue = (List<String>) value;
                    listValue.addAll(SerialUtil.stringToList(strings[2]));
                }
                break;
            }
            case "sadd": {
                Object value;
                if ((value = CACHE.get(strings[1])) == null) {
                    CACHE.put(strings[1], SerialUtil.stringToSet(strings[2]));
                } else if (!(value instanceof List)) {
                    break;
                } else {
                    Set<String> setValue = (Set<String>) value;
                    setValue.addAll(SerialUtil.stringToList(strings[2]));
                }
                break;
            }
            case "zadd": {
                try {
                    Zset zset = (Zset) CACHE.get(strings[1]);
                    String[] strings1 = (strings[2].split("È"));
                    for (String e : strings1) {
                        String[] scoreMem = e.split("©");
                        zset.zadd(Double.parseDouble(scoreMem[0]), scoreMem[1]);
                    }
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case "remove": {
                CACHE.remove(strings[1]);
                break;
            }
            default:
        }
    }

    private static void operCache() {
        try {
            while (true) {
                ConsMesService.Message message = QUEUE.take();
                switch (message.command.getType()) {
                    case "get": {
                        Object retern = CACHE.get(message.command.getKey());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("get").setResult(result).build());
                        break;
                    }
                    case "put": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object preValue;
                        if ((preValue = CACHE.get(key)) instanceof String || preValue == null) {
                            CACHE.put(key, message.command.getValue());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("put").build());
                        } else {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("put").setResult("e").build());
                        }
                        break;
                    }
                    case "incr": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        String afterValue;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) CACHE.get(message.command.getKey())) + 1);
                            CACHE.put(key, afterValue);
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("incr").setResult("e").build());
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("incr").setResult(afterValue).build());
                        break;
                    }
                    case "decr": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        String afterValue ;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) CACHE.get(message.command.getKey())) - 1);
                            CACHE.put(key, afterValue);
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("decr").setResult("e").build());
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("decr").setResult(afterValue).build());
                        break;
                    }
                    case "hput": {
                        //写持久化日志
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) !=null && !(value instanceof Map)){
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hput").setResult("e").build());
                            break;
                        }
                        Map<String,String> values = SerialUtil.stringToMap(message.command.getValue());
                        CACHE.put(key,values);
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hput").build());
                        break;
                    }
                    case "hmerge": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) == null) {
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            CACHE.put(key, values);
                        } else if (!(value instanceof Map)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hmerge").setResult("e").build());
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            for (Map.Entry<String, String> entry : values.entrySet()) {
                                mapValue.put(entry.getKey(), entry.getValue());
                            }
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hmerge").build());
                        break;
                    }
                    case "lpush": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) == null) {
                            CACHE.put(key, SerialUtil.stringToList(message.command.getValue()));
                        } else if (!(value instanceof List)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("lpush").setResult("e").build());
                            break;
                        } else {
                            List<String> listValue = (List<String>) value;
                            listValue.addAll(SerialUtil.stringToList(message.command.getValue()));
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("lpush").build());
                        break;
                    }
                    case "sadd": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) == null) {
                            CACHE.put(key, SerialUtil.stringToSet(message.command.getValue()));
                        } else if (!(value instanceof Set)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("zadd").setResult("e").build());
                            break;
                        } else {
                            Set<String> setValue = (Set<String>) value;
                            setValue.addAll(SerialUtil.stringToList(message.command.getValue()));
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("lpush").build());
                        break;
                    }
                    case "zadd": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value = CACHE.get(key);
                        if (value == null) {
                            value = new Zset();
                            String[] strings = message.command.getValue().split("È");
                            for (String e : strings) {
                                String[] scoreMem = e.split("©");
                                ((Zset) value).zadd(Double.parseDouble(scoreMem[0]), scoreMem[1]);
                            }
                            CACHE.put(key, value);
                        } else if (value instanceof Zset) {
                            String[] strings = message.command.getValue().split("È");
                            for (String e : strings) {
                                String[] scoreMem = e.split("©");
                                ((Zset) value).zadd(Double.parseDouble(scoreMem[0]), scoreMem[1]);
                            }
                        } else {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("zadd").setResult("e").build());
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("zadd").build());
                        break;
                    }
                    case "hset": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) == null) {
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            CACHE.put(key, values);
                        } else if (!(value instanceof Map)) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hset").setResult("e").build());
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            String[] keyValue = message.command.getValue().split("©");
                            mapValue.put(keyValue[0],keyValue[1]);
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hset").build());
                        break;
                    }
                    case "hget": {
                        try {
                            Map<String, String> values = (Map<String, String>) CACHE.get(message.command.getKey());
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
                            List<String> values = (List<String>) CACHE.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getList").setResult(values == null ? "null" : SerialUtil.collectionToString(values)).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getList").setResult("e").build());
                        }
                        break;
                    }
                    case "getSet": {
                        try {
                            Set<String> values = (Set<String>) CACHE.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getSet").setResult(values == null ? "null" : SerialUtil.collectionToString(values)).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getSet").setResult("e").build());
                        }
                        break;
                    }
                    case "scontain": {
                        try {
                            Set<String> values = (Set<String>) CACHE.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("scontain").setResult(String.valueOf(values.contains(message.command.getValue()))).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("scontain").setResult("e").build());
                        }
                        break;
                    }
                    case "expire": {
                        String key = message.command.getKey();
                        if (CACHE.get(key) == null) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("expire").setResult("0").build());
                        } else {
                            long expireTime = Instant.now().toEpochMilli() + (Long.parseLong(message.command.getValue()) * 1000);
                            ExpireService.setKeyAndTime(key, expireTime);
                            PersistenceService.writeExpireJournal(key +
                                    "ÈÈ" + expireTime);
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("expire").setResult("1").build());
                        }
                        break;
                    }
                    case "remove": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        CACHE.remove(message.command.getKey());
                        if (message.channelHandlerContext != null) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("remove").build());
                        }
                        break;
                    }
                    case "zrange": {
                        try {
                            Zset zset = (Zset) CACHE.get(message.command.getKey());
                            String[] startAndEnd = message.command.getValue().split("©");
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("zrange")
                                    .setResult(zset.zrange(Long.parseLong(startAndEnd[0]), Long.parseLong(startAndEnd[1]))).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("zrange").setResult("e").build());
                        }
                        break;
                    }
                    case "zrem": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zincrby": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zrank": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zrevrank": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zrevrange": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zcard": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zscore": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zcount": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case "zrangeByScore": {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
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


    public static void addMessage(ConsMesService.Message message, int threadId) {
        try {
            QUEUE.put(message, threadId);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }



}
