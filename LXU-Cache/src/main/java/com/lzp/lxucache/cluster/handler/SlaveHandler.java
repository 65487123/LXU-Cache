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

package com.lzp.lxucache.cluster.handler;


import com.lzp.lxucache.Server;
import com.lzp.lxucache.cluster.service.SlaveConsMesService;
import com.lzp.lxucache.common.protocol.CommandDTO;
import com.lzp.lxucache.common.util.FileUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @Author：luzeping
 * @Date: 2019/1/6 20:35
 */
public class SlaveHandler extends SimpleChannelInboundHandler<CommandDTO.Command> {
    private static AtomicInteger channelNum = new AtomicInteger();

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, CommandDTO.Command command) {
        SlaveConsMesService.addMessage(new SlaveConsMesService.Message(command, channelHandlerContext));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        int clientNum = channelNum.decrementAndGet();
        InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        String[] masterIpAndPort = FileUtil.getProperty("masterIpAndPort").split(":");
        if (masterIpAndPort[0].equals(inetSocketAddress.getAddress().getHostAddress())) {
            if (clientNum == 0) {
                FileUtil.setProperty("isMaster", "yes");
                Server.upgradeTomasterNode(SlaveConsMesService.laterSlaves);
                for (Channel channel : SlaveConsMesService.laterSlaves) {
                    channel.writeAndFlush(CommandDTO.Command.newBuilder().setType("refreshMaster").setKey(FileUtil.getProperty("port")).build());
                }
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        channelNum.incrementAndGet();
    }
}
