package com.lzp.service;

import com.lzp.protocol.CommandDTO;
import com.lzp.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Description:持久化服务，生成快照会阻塞线程，生成日志是另一个线程异步执行
 *
 * @author: Lu ZePing
 * @date: 2020/7/24 14:58
 */
public class PersistenceService {
    private static ObjectOutputStream objectOutputStream;

    private static BufferedWriter bufferedWriter;

    private static volatile int snapshotVersion = 0;

    private static final ExecutorService JOURNAL_THREAD_POOL = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue(), new ThreadFactoryImpl("journal handler"));

    private static final Logger logger = LoggerFactory.getLogger(PersistenceService.class);

    private static ObjectOutputStream expireObjectOutputStream;

    private static BufferedWriter expireBufferedWriter;

    private static volatile int expireSnapshotVersion = 0;

    private static AtomicInteger journalNum = new AtomicInteger();

    /**暂时先配置死为16384*/
    private final static int EX_SNAPSHOT_BATCH_COUNT_D1 = 16383;


    static {
        //生成文件目录
        FileUtil.generateFileIfNotExist(new File("./persistence/corecache"));
        FileUtil.generateFileIfNotExist(new File("./persistence/expire"));
    }


    public static void generateSnapshot(Object object) {
        snapshotVersion++;
        JOURNAL_THREAD_POOL.execute(() -> {
            //如果进程在这里挂了，这一版本号的快照没有生成，只保留前一次的快照，而版本号被改后的日志也都不会写进文件，所以就丢失了这些操作日志
            clearJournal();
            //如果进程在这里挂了，这一版本的快照没生成，日志也都被清空了，只保留了上一版本的快照（如果有的话）。所以这里挂了，信息丢失最多
            generateSnapshot0(object);
        });
    }

    /**
     * 重写快照文件，现在的做法是直接把快照文件清空，然后把新对象写进行，这个做法有个问题就是清空后和写入前这个时间段，进程挂了，
     * 那么原先的快照文件就会丢失。解决起来也比较简单，不直接重写这个快照文件，生成一个新的快照文件，新快照文件生成完再把原先快照覆盖
     * 并且这个过程会生成另一个文件记录状态，0表示正在生成新快照，1表示新快照生成完成，2表示成功覆盖快照文件。覆盖完会删除这两个临时文件
     * 下次重启后，先读有没有这个记录状态的文件，没有直接恢复，根据状态来判断要读哪个快照。
     * 由于在生成快照期间宕机是小概率事件，况且这个软件也没人用。所以现在就先这样做，以后看情况再优化
     *
     * @param
     */
    private static void generateSnapshot0(Object object) {
        try {
            //就算reset()，他也是追加写，没法覆盖文件
            if (objectOutputStream!=null) {
                objectOutputStream.close();
            }
            objectOutputStream = new ObjectOutputStream(new FileOutputStream(new File("./persistence/corecache/snapshot.txt")));
            objectOutputStream.writeObject(object);
            objectOutputStream.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }
    }

    private static void generateExpireSnapshot0(Object object) {
        try {
            if (expireObjectOutputStream!=null) {
                expireObjectOutputStream.close();
            }
            expireObjectOutputStream = new ObjectOutputStream(new FileOutputStream(new File("./persistence/expire/snapshot.txt")));
            expireObjectOutputStream.writeObject(object);
            expireObjectOutputStream.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }
    }

    /**
     * 清空持久化日志文件
     *
     * @param
     */
    private static void clearJournal() {
        try {
            if (bufferedWriter!=null) {
                bufferedWriter.close();
            }
            bufferedWriter = new BufferedWriter(new FileWriter(new File("./persistence/corecache/journal.txt")));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void clearExpireJournal() {
        try {
            if (expireBufferedWriter != null) {
                expireBufferedWriter.close();
            }
            expireBufferedWriter = new BufferedWriter(new FileWriter(new File("./persistence/expire/journal.txt")));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void generateExpireSnapshot(Object object) {
        expireSnapshotVersion++;
        JOURNAL_THREAD_POOL.execute(() -> {
            //如果进程在这里挂了，这一版本号的快照没有生成，只保留前一次的快照，而版本号被改后的日志也都不会写进文件，所以就丢失了这些操作日志
            clearExpireJournal();
            //如果进程在这里挂了，这一版本的快照没生成，日志也都被清空了，只保留了上一版本的快照（如果有的话）。所以这里挂了，信息丢失最多
            generateExpireSnapshot0(object);
        });
    }

    public static void writeJournal(CommandDTO.Command command) {
        final int snapshotVersion = PersistenceService.snapshotVersion;
        JOURNAL_THREAD_POOL.execute(() -> {
            try {
                if (snapshotVersion != PersistenceService.snapshotVersion) {
                    return;
                }
                bufferedWriter.newLine();
                bufferedWriter.write(commandToString(command));
                bufferedWriter.flush();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    private static String commandToString(CommandDTO.Command command){
        StringBuilder stringBuilder = new StringBuilder(256);
        return stringBuilder.append(command.getType()).append("ÈÈ").append(command.getKey()).append("ÈÈ")
                .append(command.getValue()).toString();
    }


    public static void writeExpireJournal(String command) {
        if (((journalNum.incrementAndGet()) & EX_SNAPSHOT_BATCH_COUNT_D1) == 0) {
            PersistenceService.generateExpireSnapshot(ExpireService.keyTimeMap);
        }
        final int expireSnapshotVersion = PersistenceService.expireSnapshotVersion;
        JOURNAL_THREAD_POOL.execute(() -> {
            try {
                if (expireSnapshotVersion != PersistenceService.expireSnapshotVersion) {
                    return;
                }
                expireBufferedWriter.newLine();
                expireBufferedWriter.write(command);
                expireBufferedWriter.flush();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

}
