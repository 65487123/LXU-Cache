package com.lzp.cluster.service;

import com.lzp.cluster.client.ClientService;
import com.lzp.common.cache.AutoDeleteMap;
import com.lzp.common.cache.Cache;
import com.lzp.common.datastructure.queue.OneToOneBlockingQueue;
import com.lzp.common.datastructure.set.Zset;
import com.lzp.common.protocol.CommandDTO;
import com.lzp.common.protocol.ResponseDTO;
import com.lzp.common.service.ExpireService;
import com.lzp.common.util.HashUtil;
import com.lzp.singlemachine.service.PersistenceService;
import com.lzp.singlemachine.service.ThreadFactoryImpl;
import com.lzp.common.util.FileUtil;
import com.lzp.common.util.SerialUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Description:只有一个消息队列的缓存服务，对应一个消费消息的线程
 * 集群环境下供从节点使用
 *
 * @author: Lu ZePing
 * @date: 2019/7/1 18:13
 */
public class SlaveConsMesService {
    private static OneToOneBlockingQueue<Message> queue;

    private static Cache<String, Object> cache;

    private static Logger logger = LoggerFactory.getLogger(SlaveConsMesService.class);

    private final static int SNAPSHOT_BATCH_COUNT_D1;

    private static int journalNum = 0;

    public static final int THREAD_NUM;

    public static List<Channel> laterSlaves = new ArrayList<>();

    static {
        int approHalfCpuCore;
        THREAD_NUM = (approHalfCpuCore = HashUtil.tableSizeFor(Runtime.getRuntime().availableProcessors()) / 2) < 1 ? 1 : approHalfCpuCore;
        SNAPSHOT_BATCH_COUNT_D1 = Integer.parseInt(FileUtil.getProperty("snapshot-batch-count")) - 1;
        ExecutorService threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), new ThreadFactoryImpl("operCache"));

        queue = new OneToOneBlockingQueue<>(Integer.parseInt(FileUtil.getProperty("queueSize")));
        threadPool.execute(() -> operCache());
    }


    public static class Message{
        CommandDTO.Command command;
        ChannelHandlerContext channelHandlerContext;

        public Message(CommandDTO.Command command,ChannelHandlerContext channelHandlerContext) {
            this.command = command;
            this.channelHandlerContext = channelHandlerContext;
        }

    }
    private static void restoreData(String[] strings){
        switch (strings[0]){
            case "put": {
                cache.put(strings[1], strings[2]);
                break;
            }
            case "incr": {
                String afterValue;
                try {
                    afterValue = String.valueOf(Integer.parseInt((String) cache.get(strings[1])) + 1);
                    cache.put(strings[1], afterValue);
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case "decr": {
                String afterValue ;
                try {
                    afterValue = String.valueOf(Integer.parseInt((String) cache.get(strings[1])) - 1);
                    cache.put(strings[1], afterValue);
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case "hput": {
                Object value;
                if ((value = cache.get(strings[1])) !=null && !(value instanceof Map)){
                    break;
                }
                Map<String,String> values = SerialUtil.stringToMap(strings[2]);
                cache.put(strings[1],values);
                break;
            }
            case "hmerge": {
                Object value;
                if ((value = cache.get(strings[1])) == null) {
                    Map<String, String> values = SerialUtil.stringToMap(strings[2]);
                    cache.put(strings[1], values);
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
                if ((value = cache.get(strings[1])) == null) {
                    //不values.addAll(Arrays.asList(message.command.getValue().split(","))) 这样写的原因是他底层也是要addAll的，没区别
                    //而且还多了一步new java.util.Arrays.ArrayList()的操作。虽然jvm在编译的时候可能就会优化成和我写的一样，但最终结果都一样，这样写直观一点。下面同样
                    cache.put(strings[1], SerialUtil.stringToList(strings[2]));
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
                if ((value = cache.get(strings[1])) == null) {
                    cache.put(strings[1], SerialUtil.stringToSet(strings[2]));
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
                    Zset zset = (Zset) cache.get(strings[1]);
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
                cache.remove(strings[1]);
                break;
            }
            default:
        }
    }

    private static void operCache() {
        try {
            while (true) {
                SlaveConsMesService.Message message = queue.take(1,TimeUnit.SECONDS);
                switch (message.command.getType()) {
                    case "get": {
                        Object retern = cache.get(message.command.getKey());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("get").setResult(result).build());
                        break;
                    }
                    case "put": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object preValue;
                        if ((preValue = cache.get(key)) instanceof String || preValue == null) {
                            cache.put(key, message.command.getValue());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("put").build());
                        } else {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("put").setResult("e").build());
                        }
                        break;
                    }
                    case "incr": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        String afterValue;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) cache.get(message.command.getKey())) + 1);
                            cache.put(key, afterValue);
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("incr").setResult("e").build());
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("incr").setResult(afterValue).build());
                        break;
                    }
                    case "decr": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        String afterValue ;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) cache.get(message.command.getKey())) - 1);
                            cache.put(key, afterValue);
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
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = cache.get(key)) !=null && !(value instanceof Map)){
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hput").setResult("e").build());
                            break;
                        }
                        Map<String,String> values = SerialUtil.stringToMap(message.command.getValue());
                        cache.put(key,values);
                        message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("hput").build());
                        break;
                    }
                    case "hmerge": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = cache.get(key)) == null) {
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            cache.put(key, values);
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
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = cache.get(key)) == null) {
                            cache.put(key, SerialUtil.stringToList(message.command.getValue()));
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
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = cache.get(key)) == null) {
                            cache.put(key, SerialUtil.stringToSet(message.command.getValue()));
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
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value = cache.get(key);
                        if (value == null) {
                            value = new Zset();
                            String[] strings = message.command.getValue().split("È");
                            for (String e : strings) {
                                String[] scoreMem = e.split("©");
                                ((Zset) value).zadd(Double.parseDouble(scoreMem[0]), scoreMem[1]);
                            }
                            cache.put(key, value);
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
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = cache.get(key)) == null) {
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            cache.put(key, values);
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
                            Map<String, String> values = (Map<String, String>) cache.get(message.command.getKey());
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
                            List<String> values = (List<String>) cache.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getList").setResult(values == null ? "null" : SerialUtil.collectionToString(values)).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getList").setResult("e").build());
                        }
                        break;
                    }
                    case "getSet": {
                        try {
                            Set<String> values = (Set<String>) cache.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getSet").setResult(values == null ? "null" : SerialUtil.collectionToString(values)).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("getSet").setResult("e").build());
                        }
                        break;
                    }
                    case "scontain": {
                        try {
                            Set<String> values = (Set<String>) cache.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("scontain").setResult(String.valueOf(values.contains(message.command.getValue()))).build());
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("scontain").setResult("e").build());
                        }
                        break;
                    }
                    case "expire": {
                        String key = message.command.getKey();
                        if (cache.get(key) == null) {
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
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        cache.remove(message.command.getKey());
                        if (message.channelHandlerContext != null) {
                            message.channelHandlerContext.writeAndFlush(ResponseDTO.Response.newBuilder().setType("remove").build());
                        }
                        break;
                    }
                    case "zrange": {
                        try {
                            Zset zset = (Zset) cache.get(message.command.getKey());
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
                    //启动后第一次来的请求一定是这个
                    case "fullSync": {
                        recoverData(message);
                        break;
                    }
                    case "notice": {
                        laterSlaves.add(ClientService.getConnection(message.command.getKey(), Integer.parseInt(message.command.getValue())));
                        break;
                    }
                    case "refreshMaster": {
                        //todo 修改配置文件，把主修改为新主
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + message.command.getType());
                }
            }
        } catch (NullPointerException e) {
            logger.info("关闭从节点消费队列服务");
        }
    }

    /**
     * Description ：读取主节点传来的持久化文件，恢复持久化数据
     *
     * @Return
     **/
    private static void recoverData(Message message){
        String snapshots = message.command.getKey();
        String journal = message.command.getValue();
        File journalFile = new File("./persistence/corecache/journal.txt");
        byte[] journalBytes = journal.getBytes();
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(journalFile);
            fileOutputStream.write(journalBytes);
            fileOutputStream.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        if ("LRU".equals(FileUtil.getProperty("strategy"))) {
            File file = new File("./persistence/corecache/snapshot.ser");
            ObjectInputStream objectInputStream = null;
            try {
                objectInputStream = new ObjectInputStream(new ByteArrayInputStream(snapshots.getBytes()));
                cache = (AutoDeleteMap<String, Object>) objectInputStream.readObject();
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
                bufferedReader = new BufferedReader(new FileReader(journalFile));
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
        } else {
            //todo 和lrucache一样的逻辑
        }
        //清空持久化文件，生成一次快照
        PersistenceService.generateSnapshot(cache);
    }


    public static void addMessage(SlaveConsMesService.Message message) {
        try {
            queue.put(message);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(),e);
        }
    }

    /**
     * Description ：升级为主节点后释放资源。
     *
     * @Return
     **/
    public static void close() {
        queue = null;
        cache = null;
        logger = null;
    }
}
