package com.lzp.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LzpMessageDecoder extends ReplayingDecoder<Void> {
    private static final Logger logger = LoggerFactory.getLogger(LzpMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        int length = byteBuf.readInt();
        byte[] content = new byte[length];
        byteBuf.readBytes(content);
        list.add(content);
    }
}
