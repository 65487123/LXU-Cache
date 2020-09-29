package com.lzp.cluster.service;

import com.lzp.cluster.client.ClientService;
import com.lzp.common.cache.AutoDeleteMap;
import com.lzp.common.cache.Cache;
import com.lzp.common.datastructure.queue.OneToOneBlockingQueue;
import com.lzp.common.datastructure.set.Zset;
import com.lzp.common.protocol.CommandDTO;
import com.lzp.common.util.HashUtil;
import com.lzp.common.service.PersistenceService;
import com.lzp.common.service.ThreadFactoryImpl;
import com.lzp.common.util.FileUtil;
import com.lzp.common.util.SerialUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

    private static ExecutorService threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactoryImpl("operCache"));

    private static ThreadPoolExecutor heartBeatThreadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactoryImpl("heartBeat"));


    public static List<Channel> laterSlaves = new ArrayList<>();

    static {
        int approHalfCpuCore;
        THREAD_NUM = (approHalfCpuCore = HashUtil.tableSizeFor(Runtime.getRuntime().availableProcessors()) / 2) < 1 ? 1 : approHalfCpuCore;
        SNAPSHOT_BATCH_COUNT_D1 = Integer.parseInt(FileUtil.getProperty("snapshot-batch-count")) - 1;
        queue = new OneToOneBlockingQueue<>(Integer.parseInt(FileUtil.getProperty("queueSize")));
        threadPool.execute(SlaveConsMesService::operCache);
        heartBeatThreadPool.execute(SlaveConsMesService::heartBeat);
    }


    public static class Message{
        CommandDTO.Command command;
        ChannelHandlerContext channelHandlerContext;

        public Message(CommandDTO.Command command,ChannelHandlerContext channelHandlerContext) {
            this.command = command;
            this.channelHandlerContext = channelHandlerContext;
        }

    }

    private static void heartBeat(){
        while (true) {
            for (Channel channel : laterSlaves) {
                channel.writeAndFlush(CommandDTO.Command.newBuilder().build());
            }
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(),e);
            }
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
        while (true) {
            try {
                SlaveConsMesService.Message message = queue.poll(1, TimeUnit.SECONDS);
                switch (message.command.getType()) {
                    case "put": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object preValue;
                        if ((preValue = cache.get(key)) instanceof String || preValue == null) {
                            cache.put(key, message.command.getValue());
                        } else {
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
                            break;
                        }
                        break;
                    }
                    case "decr": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        String afterValue;
                        try {
                            afterValue = String.valueOf(Integer.parseInt((String) cache.get(message.command.getKey())) - 1);
                            cache.put(key, afterValue);
                        } catch (Exception e) {
                            break;
                        }
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
                        if ((value = cache.get(key)) != null && !(value instanceof Map)) {
                            break;
                        }
                        Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                        cache.put(key, values);
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
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            for (Map.Entry<String, String> entry : values.entrySet()) {
                                mapValue.put(entry.getKey(), entry.getValue());
                            }
                        }
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
                            break;
                        } else {
                            List<String> listValue = (List<String>) value;
                            listValue.addAll(SerialUtil.stringToList(message.command.getValue()));
                        }
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
                            break;
                        } else {
                            Set<String> setValue = (Set<String>) value;
                            setValue.addAll(SerialUtil.stringToList(message.command.getValue()));
                        }
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
                        }
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
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            String[] keyValue = message.command.getValue().split("©");
                            mapValue.put(keyValue[0], keyValue[1]);
                        }
                        break;
                    }

                    case "expire": {
                        String key = message.command.getKey();
                        if (cache.get(key) != null) {
                            long expireTime = Instant.now().toEpochMilli() + (Long.parseLong(message.command.getValue()) * 1000);
                            SlaveExpireService.setKeyAndTime(key, expireTime);
                            PersistenceService.writeExpireJournal(key +
                                    "ÈÈ" + expireTime);
                        }
                        break;
                    }
                    case "remove": {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(cache);
                        }
                        PersistenceService.writeJournal(message.command);
                        cache.remove(message.command.getKey());
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
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) message.channelHandlerContext.channel().remoteAddress();
                        String newMaster = inetSocketAddress.getHostString() + ":" + message.command.getKey();
                        FileUtil.setProperty("masterIpAndPort", newMaster);
                        break;
                    }
                    case "getMaster": {
                        message.channelHandlerContext.writeAndFlush(FileUtil.getProperty("masterIpAndPort").getBytes(StandardCharsets.UTF_8));
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + message.command.getType());
                }
            } catch (InterruptedException e) {
                //一秒钟内没请求过来
            } catch (NullPointerException e) {
                logger.info("关闭从节点消费队列服务");
                break;
            }
        }
    }

    /**
     * Description ：读取主节点传来的持久化文件，恢复持久化数据
     *
     * @Return
     **/
    private static void recoverData(Message message) {
        String[] snaps = message.command.getKey().split("■■■■■");
        String[] jours = message.command.getValue().split("■■■■■");
        String snapshots = snaps[0];
        String journal = jours.length > 0 ? jours[0] : "";
        String expireSnap = snaps[1];
        String expireJour = jours.length == 2 ? jours[1] : "";
        FileUtil.generateFileIfNotExist(new File("./persistence/corecache"));
        FileUtil.generateFileIfNotExist(new File("./persistence/expire"));
        File journalFile = new File("./persistence/corecache/journal.txt");
        byte[] journalBytes = SerialUtil.toByteArray(journal);
        FileOutputStream jourfileOutputStream = null;
        FileOutputStream expJourFileOutputStream = null;
        FileOutputStream expSnapFileOutputStream = null;
        try {
            jourfileOutputStream = new FileOutputStream(journalFile);
            jourfileOutputStream.write(journalBytes);
            jourfileOutputStream.flush();
            expJourFileOutputStream = new FileOutputStream("./persistence/expire/journal.txt");
            expJourFileOutputStream.write(SerialUtil.toByteArray(expireJour));
            expJourFileOutputStream.flush();
            expSnapFileOutputStream = new FileOutputStream("./persistence/expire/snapshot.ser");
            expSnapFileOutputStream.write(SerialUtil.toByteArray(expireSnap));
            expSnapFileOutputStream.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            FileUtil.closeResource(jourfileOutputStream,expJourFileOutputStream,expSnapFileOutputStream);
        }
        if ("LRU".equals(FileUtil.getProperty("strategy"))) {
            ObjectInputStream objectInputStream = null;
            try {
                objectInputStream = new ObjectInputStream(new ByteArrayInputStream(SerialUtil.toByteArray(snapshots)));
                cache = (AutoDeleteMap<String, Object>) objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException();
            } catch (ClassCastException e) {
                logger.error("持久化文件的缓存淘汰策略和配置文件不一致");
                throw e;
            } finally {
                FileUtil.closeResource(objectInputStream);
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
                FileUtil.closeResource(bufferedReader);
            }
        } else {
            //todo 和lrucache一样的逻辑
        }
        //清空持久化文件，生成一次快照
        PersistenceService.generateSnapshot(cache);
        try {
            Class.forName("com.lzp.cluster.service.SlaveExpireService");
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(),e);
        }
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
        threadPool.shutdown();
        threadPool = null;
        cache = null;
        logger = null;
    }
}
