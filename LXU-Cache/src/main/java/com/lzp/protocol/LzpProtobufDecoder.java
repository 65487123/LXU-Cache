package com.lzp.protocol;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.MessageLite;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/6/30 17:32
 */
public class LzpProtobufDecoder extends MessageToMessageDecoder<byte[]> {



    private final MessageLite prototype;

    /**
     * Creates a new instance.
     */
    public LzpProtobufDecoder(MessageLite prototype) {
        this(prototype, null);
    }

    public LzpProtobufDecoder(MessageLite prototype, ExtensionRegistry extensionRegistry) {
        this(prototype, (ExtensionRegistryLite) extensionRegistry);
    }

    public LzpProtobufDecoder(MessageLite prototype, ExtensionRegistryLite extensionRegistry) {
        if (prototype == null) {
            throw new NullPointerException("prototype");
        }
        this.prototype = prototype.getDefaultInstanceForType();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, byte[] msg, List<Object> out)
            throws Exception {
        out.add(prototype.getParserForType().parseFrom(msg, 0, msg.length));
    }
}
