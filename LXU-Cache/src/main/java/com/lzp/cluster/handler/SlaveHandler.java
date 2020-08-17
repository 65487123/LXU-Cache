package com.lzp.cluster.handler;


import com.lzp.Server;
import com.lzp.cluster.service.SlaveConsMesService;
import com.lzp.common.protocol.CommandDTO;
import com.lzp.common.util.FileUtil;
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
