package com.lzp.nettyserver;

import com.lzp.protocol.CommandDTO;
import com.lzp.service.CacheService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @Author：luzeping
 * @Date: 2020/1/6 20:35
 */
public class Handler extends SimpleChannelInboundHandler<CommandDTO.Command> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, CommandDTO.Command command) throws Exception {
        CacheService.addMessage(new CacheService.Message(command,channelHandlerContext));
    }
}
