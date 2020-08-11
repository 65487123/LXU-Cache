package com.lzp.cluster.handler;


import com.lzp.cluster.service.SlaveConsMesService;
import com.lzp.common.protocol.CommandDTO;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;



/**
 * @Author：luzeping
 * @Date: 2019/1/6 20:35
 */
public class SlaveHandler extends SimpleChannelInboundHandler<CommandDTO.Command> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, CommandDTO.Command command) {
        SlaveConsMesService.addMessage(new SlaveConsMesService.Message(command, channelHandlerContext));
    }

}
