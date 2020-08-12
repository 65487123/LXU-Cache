package com.lzp.cluster.client;

import com.lzp.common.protocol.ResponseDTO;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/7/1 12:59
 */
public class ClientHandler extends SimpleChannelInboundHandler<ResponseDTO.Response> {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);



    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ResponseDTO.Response msg) throws Exception {

    }
}
