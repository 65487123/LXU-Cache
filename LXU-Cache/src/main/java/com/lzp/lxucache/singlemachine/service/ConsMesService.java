 /* Copyright zeping lu
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */


package com.lzp.lxucache.singlemachine.service;

import com.lzp.lxucache.common.cache.AutoDeleteMap;
import com.lzp.lxucache.common.cache.Cache;
import com.lzp.lxucache.common.cache.LfuCache;
import com.lzp.lxucache.common.constant.ReqName;
import com.lzp.lxucache.common.constant.Const;
import com.lzp.lxucache.common.datastructure.queue.NoLockBlockingQueue;
import com.lzp.lxucache.common.datastructure.set.Zset;
import com.lzp.lxucache.common.protocol.CommandDTO;
import com.lzp.lxucache.common.service.PersistenceService;
import com.lzp.lxucache.common.service.ThreadFactoryImpl;
import com.lzp.lxucache.common.util.FileUtil;
import com.lzp.lxucache.common.util.HashUtil;
import com.lzp.lxucache.common.util.SerialUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
        if (Const.LRU.equals(FileUtil.getProperty("strategy"))) {
            File file = new File(Const.SNAPSHOT_PATH);
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
                    FileUtil.closeResource(objectInputStream);
                }
                BufferedReader bufferedReader = null;
                try {
                    bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(Const.JOURNAL_PATH), StandardCharsets.UTF_8));
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
            case ReqName.PUT: {
                CACHE.put(strings[1], strings[2]);
                break;
            }
            case ReqName.INCR: {
                String afterValue;
                try {
                    afterValue = String.valueOf(Integer.parseInt((String) CACHE.get(strings[1])) + 1);
                    CACHE.put(strings[1], afterValue);
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case ReqName.DECR: {
                String afterValue ;
                try {
                    afterValue = String.valueOf(Integer.parseInt((String) CACHE.get(strings[1])) - 1);
                    CACHE.put(strings[1], afterValue);
                } catch (Exception e) {
                    break;
                }
                break;
            }
            case ReqName.HPUT: {
                Object value;
                if ((value = CACHE.get(strings[1])) !=null && !(value instanceof Map)){
                    break;
                }
                Map<String,String> values = SerialUtil.stringToMap(strings[2]);
                CACHE.put(strings[1],values);
                break;
            }
            case ReqName.HMERGE: {
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
            case ReqName.LPUSH: {
                Object value;
                if ((value = CACHE.get(strings[1])) == null) {
                    CACHE.put(strings[1], SerialUtil.stringToList(strings[2]));
                } else if (!(value instanceof List)) {
                    break;
                } else {
                    List<String> listValue = (List<String>) value;
                    listValue.addAll(SerialUtil.stringToList(strings[2]));
                }
                break;
            }
            case ReqName.SADD: {
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
            case ReqName.ZADD: {
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
            case ReqName.REMOVE: {
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
                    case ReqName.GET: {
                        Object retern = CACHE.get(message.command.getKey());
                        String result = retern == null ? "null" : retern.toString();
                        message.channelHandlerContext.writeAndFlush(result.getBytes(StandardCharsets.UTF_8));
                        break;
                    }
                    case ReqName.PUT: {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object preValue;
                        if ((preValue = CACHE.get(key)) instanceof String || preValue == null) {
                            CACHE.put(key, message.command.getValue());
                            message.channelHandlerContext.writeAndFlush(new byte[1]);
                        } else {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.INCR: {
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
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(afterValue.getBytes(StandardCharsets.UTF_8));
                        break;
                    }
                    case ReqName.DECR: {
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
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        }
                        message.channelHandlerContext.writeAndFlush(afterValue.getBytes(StandardCharsets.UTF_8));
                        break;
                    }
                    case ReqName.HPUT: {
                        //写持久化日志
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) !=null && !(value instanceof Map)){
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        }
                        Map<String,String> values = SerialUtil.stringToMap(message.command.getValue());
                        CACHE.put(key,values);
                        message.channelHandlerContext.writeAndFlush(new byte[1]);
                        break;
                    }
                    case ReqName.HMERGE: {
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
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            Map<String, String> values = SerialUtil.stringToMap(message.command.getValue());
                            for (Map.Entry<String, String> entry : values.entrySet()) {
                                mapValue.put(entry.getKey(), entry.getValue());
                            }
                        }
                        message.channelHandlerContext.writeAndFlush(new byte[1]);
                        break;
                    }
                    case ReqName.LPUSH: {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) == null) {
                            CACHE.put(key, SerialUtil.stringToList(message.command.getValue()));
                        } else if (!(value instanceof List)) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        } else {
                            List<String> listValue = (List<String>) value;
                            listValue.addAll(SerialUtil.stringToList(message.command.getValue()));
                        }
                        message.channelHandlerContext.writeAndFlush(new byte[1]);
                        break;
                    }
                    case ReqName.SADD: {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        String key = message.command.getKey();
                        Object value;
                        if ((value = CACHE.get(key)) == null) {
                            CACHE.put(key, SerialUtil.stringToSet(message.command.getValue()));
                        } else if (!(value instanceof Set)) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        } else {
                            Set<String> setValue = (Set<String>) value;
                            setValue.addAll(SerialUtil.stringToList(message.command.getValue()));
                        }
                        message.channelHandlerContext.writeAndFlush(new byte[1]);
                        break;
                    }
                    case ReqName.ZADD: {
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
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        message.channelHandlerContext.writeAndFlush(new byte[1]);
                        break;
                    }
                    case ReqName.HSET: {
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
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                            break;
                        } else {
                            Map<String, String> mapValue = (Map<String, String>) value;
                            String[] keyValue = message.command.getValue().split("©");
                            mapValue.put(keyValue[0],keyValue[1]);
                        }
                        message.channelHandlerContext.writeAndFlush(new byte[1]);
                        break;
                    }
                    case ReqName.HGET: {
                        try {
                            Map<String, String> values = (Map<String, String>) CACHE.get(message.command.getKey());
                            if (values == null) {
                                message.channelHandlerContext.writeAndFlush("null".getBytes(StandardCharsets.UTF_8));
                            } else {
                                String result;
                                message.channelHandlerContext.writeAndFlush((result = values.get(message.command.getValue())) == null ? "null".getBytes(StandardCharsets.UTF_8) : result.getBytes(StandardCharsets.UTF_8));
                            }
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.GET_LIST: {
                        try {
                            List<String> values = (List<String>) CACHE.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(values == null ? "null".getBytes(StandardCharsets.UTF_8) : SerialUtil.collectionToString(values).getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.GET_SET: {
                        try {
                            Set<String> values = (Set<String>) CACHE.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(values == null ? "null".getBytes(StandardCharsets.UTF_8) : SerialUtil.collectionToString(values).getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.SCONTAIN: {
                        try {
                            Set<String> values = (Set<String>) CACHE.get(message.command.getKey());
                            message.channelHandlerContext.writeAndFlush(String.valueOf(values.contains(message.command.getValue())).getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.EXPIRE: {
                        String key = message.command.getKey();
                        if (CACHE.get(key) == null) {
                            message.channelHandlerContext.writeAndFlush("0".getBytes(StandardCharsets.UTF_8));
                        } else {
                            long expireTime = Instant.now().toEpochMilli() + (Long.parseLong(message.command.getValue()) * 1000);
                            ExpireService.setKeyAndTime(key, expireTime);
                            PersistenceService.writeExpireJournal(key +
                                    "ÈÈ" + expireTime);
                            message.channelHandlerContext.writeAndFlush("1".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.REMOVE: {
                        if (((++journalNum) & SNAPSHOT_BATCH_COUNT_D1) == 0) {
                            PersistenceService.generateSnapshot(CACHE);
                        }
                        PersistenceService.writeJournal(message.command);
                        CACHE.remove(message.command.getKey());
                        if (message.channelHandlerContext != null) {
                            message.channelHandlerContext.writeAndFlush(new byte[1]);
                        }
                        break;
                    }
                    case ReqName.ZRANGE: {
                        try {
                            Zset zset = (Zset) CACHE.get(message.command.getKey());
                            String[] startAndEnd = message.command.getValue().split("©");
                            message.channelHandlerContext.writeAndFlush(zset.zrange(Long.parseLong(startAndEnd[0]), Long.parseLong(startAndEnd[1])).getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            message.channelHandlerContext.writeAndFlush("e".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    case ReqName.ZREM: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZINCRBY: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZRANK: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZREVRANK: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZREVRANGE: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZCARD: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZSCORE: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZCOUNT: {
                        //todo 本地调用Zset其实都实现了，rpc暂时没时间写，有空补上
                        break;
                    }
                    case ReqName.ZRANGEBYSCORE: {
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
