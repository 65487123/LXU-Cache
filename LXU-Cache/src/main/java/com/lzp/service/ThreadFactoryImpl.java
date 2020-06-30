package com.lzp.service;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/6/2 15:19
 */
public class ThreadFactoryImpl implements ThreadFactory {
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    private final int priority;

    public ThreadFactoryImpl(String namePrefix,int priority) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix + "-thread-";
        this.priority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
        t.setPriority(priority);
        return t;
    }

}