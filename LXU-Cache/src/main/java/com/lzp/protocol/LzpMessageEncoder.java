package com.lzp.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class LzpMessageEncoder extends MessageToByteEncoder<byte[]> {


    protected void encode(ChannelHandlerContext channelHandlerContext, byte[] content, ByteBuf byteBuf)  {
        byteBuf.writeInt(content.length);
        byteBuf.writeBytes(content);
    }
}
