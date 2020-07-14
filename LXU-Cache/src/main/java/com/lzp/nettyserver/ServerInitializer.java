package com.lzp.nettyserver;

import com.lzp.protocol.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;

/**
 * @Author：luzeping
 * @Date: 2020/1/6 20:39
 */
public class ServerInitializer extends ChannelInitializer {
    @Override
    protected void initChannel(Channel channel) {
        channel.pipeline().addLast(new LzpMessageDecoder()).addLast(new LzpProtobufDecoder(CommandDTO.Command.getDefaultInstance()))
                .addLast(new LzpMessageEncoder()).addLast(new LzpProtobufEncoder())
                .addLast("handler1",new Handler());
    }
}
