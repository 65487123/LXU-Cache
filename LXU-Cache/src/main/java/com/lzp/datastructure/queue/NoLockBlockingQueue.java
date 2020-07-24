package com.lzp.datastructure.queue;

import java.util.concurrent.atomic.AtomicReferenceArray;


/**
 * Description:高性能阻塞队列，适用于多个生产者对一个消费者（线程),无锁设计，并且解决了伪共享问题。
 *
 * @author: Lu ZePing
 * @date: 2020/7/20 12:19
 */
public class NoLockBlockingQueue<E> extends BlockingQueueAdapter<E> {
    private AtomicReferenceArray<E>[] array;
    private final int m;
    /** @sun.misc.Contended 这个注解修饰数组，数组里的元素可能还是会出现伪共享，所以还是自己填充解决好了*/

    private int[] head;
    private int[] tail;



    public NoLockBlockingQueue(int preferCapacity, int threadSum) {
        int capacity = tableSizeFor(preferCapacity);
        int capacityPerSlot = capacity / threadSum;
        array = new AtomicReferenceArray[threadSum];
        for (int i = 0; i < array.length; i ++) {
            array[i] = new AtomicReferenceArray<>(capacityPerSlot);
        }
        //int占4个字节
        head = new int[16 * threadSum];
        tail = new int[16 * threadSum];
        m = capacityPerSlot - 1;
    }

    @Override
    public void put(E obj, int threadId) throws InterruptedException {
        int p = head[16*threadId]++ & m;
        while (array[threadId].get(p) != null) {
            Thread.yield();
        }
        array[threadId].set(p,obj);
    }


    @Override
    public E take() throws InterruptedException {
        Object r;
        while (true) {
            for (int i = 0; i < tail.length; i+=16) {
                int p = tail[i] & this.m;
                if ((r = array[i/16].get(p)) != null) {
                    array[i/16].set(p, null);
                    tail[i]++;
                    return (E) r;
                }
            }
            Thread.yield();
        }
    }

}