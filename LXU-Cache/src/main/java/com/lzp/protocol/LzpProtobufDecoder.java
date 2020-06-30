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
    private static final boolean HAS_PARSER;

    static {
        boolean hasParser = false;
        try {
            // MessageLite.getParserForType() is not available until protobuf 2.5.0.
            MessageLite.class.getDeclaredMethod("getParserForType");
            hasParser = true;
        } catch (Throwable t) {
            // Ignore
        }

        HAS_PARSER = hasParser;
    }

    private final MessageLite prototype;
    private final ExtensionRegistryLite extensionRegistry;

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
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, byte[] msg, List<Object> out)
            throws Exception {
        if (extensionRegistry == null) {
            if (HAS_PARSER) {
                out.add(prototype.getParserForType().parseFrom(msg, 0, msg.length));
            } else {
                out.add(prototype.newBuilderForType().mergeFrom(msg, 0, msg.length).build());
            }
        } else {
            if (HAS_PARSER) {
                out.add(prototype.getParserForType().parseFrom(
                        msg, 0, msg.length, extensionRegistry));
            } else {
                out.add(prototype.newBuilderForType().mergeFrom(
                        msg, 0, msg.length, extensionRegistry).build());
            }
        }
    }
}
