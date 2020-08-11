package com.lzp.cluster.client;

import com.lzp.common.protocol.ResponseDTO;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/7/1 12:59
 */
public class ClientHandler extends SimpleChannelInboundHandler<ResponseDTO.Response> {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    /**
     * key是client对应的channel，value是client对应的线程和当次操作的结果(如果有的话)
     **/
    public static Map<Channel, ThreadResultObj> channelResultMap = new ConcurrentHashMap<>(256);

    public static class ThreadResultObj{
        private Thread thread;
        private String result;

        public ThreadResultObj(Thread thread, String result) {
            this.thread = thread;
            this.result = result;
        }

        public void setThread(Thread thread) {
            this.thread = thread;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public Thread getThread() {
            return thread;
        }

        public String getResult() {
            return result;
        }
    }



    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        channelResultMap.put(ctx.channel(),new ThreadResultObj(null,null));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channelResultMap.remove(channel);
        logger.info(channel.id() + "与服务端断开连接");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ResponseDTO.Response msg) throws Exception {
        ThreadResultObj threadResultObj = channelResultMap.get(ctx.channel());
        threadResultObj.result = msg.getResult();
        LockSupport.unpark(threadResultObj.thread);
    }
}
