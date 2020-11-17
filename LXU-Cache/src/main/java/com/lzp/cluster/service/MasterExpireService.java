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

package com.lzp.cluster.service;

import com.lzp.common.protocol.CommandDTO;
import com.lzp.common.service.PersistenceService;
import com.lzp.common.service.ThreadFactoryImpl;
import com.lzp.common.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Description:用来处理key超时过期相关事件的服务，独立一个线程
 * <p>
 * key过期处理主要有三种办法：
 * 1、在下一次访问key时判断这个key是否设置了超时时间、是否已经过期，如果过期了，删除key，返回null
 * 2、收到给key设置超时时间的请求后就开一个线程，sleep超时时间的秒数后删除这个key。
 * 3、new一个map用来存放设置超时时间的key。独立起个线程，每个一段时间轮询这个map中的key是否已经超时如果超时了，就把key从缓存中删除
 * <p>
 * 第一种方法影响读缓存效率，所以不考虑
 * 第二种办法可能会导致线程数量太多，也会影响性能。并且，一个key的超时时间只能设置一次，不能覆盖超时时间。
 * 所以这里选择用第三种方法来处理过期key。适量浪费单个cpu的资源问题也不是很大
 *
 * @author: Lu ZePing
 * @date: 2019/7/14 11:07
 */
public class MasterExpireService {
    /**
     * 用来存放设置过期时间的key以及它的具体过期时刻
     */
    public static Map<String, Long> keyTimeMap;

    /**
     * 处理过期key的线程
     */
    private static final ExecutorService threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue(1), new ThreadFactoryImpl("expire handler"));

    private static final long pollingInterval;

    private static final Logger logger = LoggerFactory.getLogger(MasterExpireService.class);

    private static Boolean isMaster = !"yes".equals(FileUtil.getProperty("cluster-enabled")) ? null : "yes".equals(FileUtil.getProperty("isMaster"));

    static {
        File file = new File("./persistence/expire/snapshot.ser");
        if (!file.exists()) {
            keyTimeMap = new ConcurrentHashMap<>(16);
        } else {
            ObjectInputStream objectInputStream = null;
            try {
                objectInputStream = new ObjectInputStream(new FileInputStream(file));
                keyTimeMap = (ConcurrentHashMap) objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException();
            } finally {
                FileUtil.closeResource(objectInputStream);
            }
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream("./persistence/expire/journal.txt"), "UTF-8"));
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
        //清空持久化文件，生成一次快照
        PersistenceService.generateExpireSnapshot(keyTimeMap);
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
                            MasterConsMesService.addMessage(new MasterConsMesService.Message(CommandDTO.Command.newBuilder().setKey(entry.getKey()).setType("remove").build(), null), 0);
                            PersistenceService.writeExpireJournal(entry.getKey());
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

    private static void restoreData(String[] strings) {
        if (strings.length == 2) {
            keyTimeMap.put(strings[0], Long.parseLong(strings[1]));
        } else {
            keyTimeMap.remove(strings[0]);
        }
    }

    public static void setKeyAndTime(String key, Long expireTime) {
        keyTimeMap.put(key, expireTime);
    }


    public static void upgrade() {
        isMaster = true;
    }


}
