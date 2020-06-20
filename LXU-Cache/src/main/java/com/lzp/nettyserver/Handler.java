package com.lzp.nettyserver;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author：luzeping
 * @Date: 2020/1/6 20:35
 */
public class Handler extends SimpleChannelInboundHandler {
    private static List<Channel> channelList = new CopyOnWriteArrayList<>();

    static {
        new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 2000; i++) {
                    for (Channel channel : channelList) {
                        channel.writeAndFlush(Unpooled.copiedBuffer(Instant.now().atZone(ZoneId.systemDefault())+" 客户端"+channel.remoteAddress().toString() + ":当前有" + channelList.size() + "个连接", Charset.forName("GBK")));
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {

    }
}
