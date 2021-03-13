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

package com.lzp.singlemachine.handler;

import com.lzp.common.protocol.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * @Author：luzeping
 * @Date: 2019/1/6 20:39
 */
public class SocketChannelInitializer extends ChannelInitializer {
    @Override
    protected void initChannel(Channel channel) {
        channel.pipeline().addLast(new IdleStateHandler(15, Integer.MAX_VALUE, Integer.MAX_VALUE)).addLast(new LzpMessageDecoder(true))
                .addLast(new LzpProtobufDecoder(CommandDTO.Command.getDefaultInstance()))
                .addLast(new LzpMessageEncoder()).addLast(new LzpProtobufEncoder())
                .addLast("handler1", new Handler());
    }
}
