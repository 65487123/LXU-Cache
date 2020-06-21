package com.lzp.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

public class LzpMessageDecoder extends ReplayingDecoder<Void> {
    private static final Logger logger = LoggerFactory.getLogger(LzpMessageDecoder.class);
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        //将得到的二进制字节码转成MessageProtocol
        int length = byteBuf.readInt();
        byte[] content = new byte[length];
        byteBuf.readBytes(content);
        ByteArrayInputStream is = new ByteArrayInputStream(content);
        try {
            ObjectInputStream oit = new ObjectInputStream(is);
            list.add(oit.readObject());
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }
    }
}
