package com.lzp.common.datastructure.queue;

import com.lzp.common.util.HashUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;


/**
 * Description:高性能阻塞队列，适用于一个生产者对一个消费者（线程),无锁设计，并且解决了伪共享问题。
 *
 * @author: Lu ZePing
 * @date: 2019/7/20 12:19
 */
public class OneToOneBlockingQueue<E> extends BlockingQueueAdapter<E> {
    /**
     * 指针压缩后4字节
     */
    private AtomicReferenceArray<E> array;
    /**
     * 4字节，加上对象头12字节，一共20字节，还差44字节
     */
    private final int m;

    private int[] head = new int[27];

    private int[] tail = new int[16];


    public OneToOneBlockingQueue(int preferCapacity) {
        int capacity = HashUtil.tableSizeFor(preferCapacity);
        array = new AtomicReferenceArray(capacity);
        m = capacity - 1;
    }

    @Override
    public void put(E obj) throws InterruptedException {

        int p = head[11]++ & m;
        while (array.get(p) != null) {
            Thread.yield();
        }
        array.set(p, obj);
    }


    @Override
    public E take() throws InterruptedException {
        Object e;
        int p = tail[0]++ & m;
        while ((e = array.get(p)) == null) {
            Thread.yield();
        }
        array.set(p, null);
        return (E) e;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long now = 0;
        long time = unit.toMillis(timeout);
        Object e;
        int p = tail[0]++ & m;
        while ((e = array.get(p)) == null) {
            if (now == 0) {
                now = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - now > time) {
                tail[0]--;
                throw new InterruptedException();
            } else {
                Thread.yield();
            }
        }
        array.set(p, null);
        return (E) e;
    }
}