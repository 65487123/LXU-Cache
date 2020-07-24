package com.lzp.service;

import com.alibaba.fastjson.JSON;
import com.lzp.protocol.CommandDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Description:持久化服务，由一个独立线程异步执行
 *
 * @author: Lu ZePing
 * @date: 2020/7/24 14:58
 */
public class PersistenceService {

    private static final ExecutorService threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue(100), new ThreadFactoryImpl("persistence handler"));

    private static final Logger logger = LoggerFactory.getLogger(PersistenceService.class);

    public void generateSnapshot(Object object){
        JSON.toJSONString(object);
    }

    public void writeJournal(CommandDTO.Command command){

    }


}
