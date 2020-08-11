package com.lzp.common.datastructure.queue;

import com.lzp.common.util.HashUtil;

import java.util.concurrent.atomic.AtomicReferenceArray;


/**
 * Description:高性能阻塞队列，适用于一个生产者对一个消费者（线程),无锁设计，并且解决了伪共享问题。
 *
 * @author: Lu ZePing
 * @date: 2019/7/20 12:19
 */
public class OneToOneBlockingQueue<E> extends BlockingQueueAdapter<E> {
    private AtomicReferenceArray<E> array;
    private final int m;

    @sun.misc.Contended
    private int head;

    @sun.misc.Contended
    private int tail;


    public OneToOneBlockingQueue(int preferCapacity) {
        int capacity = HashUtil.tableSizeFor(preferCapacity);
        array = new AtomicReferenceArray(capacity);
        m = capacity - 1;
    }

    @Override
    public void put(E obj) throws InterruptedException {

        int p = head++ & m;
        while (array.get(p) != null) {
            Thread.yield();
        }
        array.set(p, obj);
    }


    @Override
    public E take() throws InterruptedException {

        Object e;
        int p = tail++ & m;
        while ((e = array.get(p)) == null) {
            Thread.yield();
        }
        array.set(p, null);
        return (E) e;
    }

}