package com.lzp.nettyserver;

import com.lzp.protocol.LzpMessageDecoder;
import com.lzp.protocol.LzpMessageEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;

/**
 * @Author：luzeping
 * @Date: 2020/1/6 20:39
 */
public class ServerInitializer extends ChannelInitializer {
    @Override
    protected void initChannel(Channel channel) {
        channel.pipeline().addLast(new LzpMessageDecoder()).addLast(new LzpMessageEncoder()).addLast("handler1",new Handler());
    }
}
