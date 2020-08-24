package com.lzp.cluster.handler;

import com.lzp.common.protocol.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * @Author：luzeping
 * @Date: 2019/1/6 20:39
 */
public class MasterChannelInitializer extends ChannelInitializer {
    @Override
    protected void initChannel(Channel channel) {
        channel.pipeline().addLast(new IdleStateHandler(15,Integer.MAX_VALUE,Integer.MAX_VALUE)).addLast(new LzpMessageDecoder())
                .addLast(new LzpProtobufDecoder(CommandDTO.Command.getDefaultInstance()))
                .addLast(new LzpMessageEncoder()).addLast(new LzpProtobufEncoder())
                .addLast("handler1",new MasterHandler());
    }
}
