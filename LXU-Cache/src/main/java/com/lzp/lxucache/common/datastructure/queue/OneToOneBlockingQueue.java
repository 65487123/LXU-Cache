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

package com.lzp.lxucache.common.datastructure.queue;


import com.lzp.lxucache.common.util.HashUtil;

import java.util.concurrent.TimeUnit;


/**
 * Description:高性能阻塞队列，适用于一个生产者对一个消费者（线程),无锁设计，并且解决了伪共享问题。
 *
 * @author: Lu ZePing
 * @date: 2019/7/20 12:19
 */
public class OneToOneBlockingQueue<E> extends BlockingQueueAdapter<E> {

    /**
     * 指针压缩后4字节
     *
     * 这里可能会出现可见性问题，但是完全不会出任何问题：
     *
     * 对生产者来说，先cas拿到将要存放元素的位置,然后判断当前位置的元素是否已经被消费(是否是null),
     * 如果为null，才放入元素，如果出现可见性问题，可能会出现位置已经为null了，但是生产者看起来
     * 还是null。这样造成的后果只是空轮循几次而已，结果并不会出错。(这其实是面向实现编程而不是面向
     * 规范编程了,不是非常严谨,不加volatile,按照java规范,其实是有可能出现永远不可见的情况的,
     * Thread.yield()以及Thread.sleep(1)并没有保证重新取得cpu资源时会刷新寄存器,而且也不是
     * 所有CPU都保证支持缓存一致性协议。。只能说根据绝大部份底层实现,不会出现一直不可见的情况)
     *
     * 同样，对消费者来说。如果出现可见性问题，最多就是当前位置已经有元素了，而消费者没看到。造成的
     * 后果也是出现空轮询几次。
     *
     *
     * 由于每部份数组块只被同一个线程操作，所以写数据的时候也不需要进行cas（不可能会出现两个写线程
     * 同时在等一个位置被释放）
     */
    private final E[] ARRAY;
    /**
     * 4字节，加上对象头12字节，一共20字节，还差44字节
     */
    private final int m;

    private final int[] HEAD = new int[27];

    private final int[] TAIL = new int[16];


    public OneToOneBlockingQueue(int preferCapacity) {
        int capacity = HashUtil.tableSizeFor(preferCapacity);
        ARRAY = (E[]) new Object[capacity];
        m = capacity - 1;
    }

    @Override
    public void put(E obj) throws InterruptedException {

        int p = HEAD[11]++ & m;
        while (ARRAY[p] != null) {
            Thread.yield();
        }
        ARRAY[p] = obj;
    }


    @Override
    public E take() throws InterruptedException {
        E e;
        int p = TAIL[0]++ & m;
        while ((e = ARRAY[p]) == null) {
            Thread.yield();
        }
        ARRAY[p] = null;
        return  e;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long now = 0;
        long time = unit.toMillis(timeout);
        E e;
        int p = TAIL[0]++ & m;
        while ((e = ARRAY[p]) == null) {
            if (now == 0) {
                now = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - now > time) {
                TAIL[0]--;
                throw new InterruptedException();
            } else {
                Thread.yield();
            }
        }
        ARRAY[p] = null;
        return e;
    }
}