package com.lzp.common.datastructure.queue;

import com.lzp.common.util.HashUtil;

import java.util.concurrent.atomic.AtomicReferenceArray;


/**
 * Description:高性能阻塞队列，适用于多个生产者对一个消费者（线程),无锁设计，并且解决了伪共享问题。
 *
 * @author: Lu ZePing
 * @date: 2019/7/20 12:19
 */
public class NoLockBlockingQueue<E> extends BlockingQueueAdapter<E> {
    /**
     * 指针压缩后4字节
     */
    private AtomicReferenceArray<E>[] array;
    /**
     * 4字节，加上对象头12字节，一共20字节，还差44字节
     */
    private final int m;

    private int[] padding = new int[11];
    /**
     * @sun.misc.Contended 这个注解原理是前后都填充128字节，优点浪费内存，所以还是自己填充好了
     */
    private int[] head;

    private int[] tail;


    public NoLockBlockingQueue(int preferCapacity, int threadSum) {
        int capacity = HashUtil.tableSizeFor(preferCapacity);
        int capacityPerSlot = capacity / threadSum;
        array = new AtomicReferenceArray[threadSum];
        for (int i = 0; i < array.length; i++) {
            array[i] = new AtomicReferenceArray<>(capacityPerSlot);
        }
        //int占4个字节
        head = new int[16 * threadSum];
        tail = new int[16 * threadSum];
        m = capacityPerSlot - 1;
    }

    @Override
    public void put(E obj, int threadId) throws InterruptedException {
        int p = head[16 * threadId]++ & m;
        while (array[threadId].get(p) != null) {
            Thread.yield();
        }
        array[threadId].set(p, obj);
    }


    @Override
    public E take() throws InterruptedException {
        Object r;
        while (true) {
            for (int i = 0; i < tail.length; i += 16) {
                int p = tail[i] & this.m;
                if ((r = array[i / 16].get(p)) != null) {
                    array[i / 16].set(p, null);
                    tail[i]++;
                    return (E) r;
                }
            }
            Thread.yield();
        }
    }

}