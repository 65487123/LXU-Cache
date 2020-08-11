package com.lzp.common.protocol;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

import java.util.List;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2019/6/30 17:07
 */
public class LzpProtobufEncoder extends ProtobufEncoder {

    @Override
    protected void encode(ChannelHandlerContext ctx, MessageLiteOrBuilder msg, List<Object> out) {
        out.add(((MessageLite) msg).toByteArray());
    }
}
