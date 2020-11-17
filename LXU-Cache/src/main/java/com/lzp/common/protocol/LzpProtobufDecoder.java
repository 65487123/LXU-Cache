 /* Copyright zeping lu
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */

package com.lzp.common.protocol;

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
 * @date: 2019/6/30 17:32
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
