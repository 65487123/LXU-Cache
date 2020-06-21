package com.lzp.nettyserver;

import com.lzp.protocol.CommandDTO;
import com.lzp.protocol.ResponseDTO;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @Author：luzeping
 * @Date: 2020/1/6 20:35
 */
public class Handler extends SimpleChannelInboundHandler<CommandDTO> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, CommandDTO commandDTO) throws Exception {
        switch (commandDTO.getType()) {
            case "get":
                channelHandlerContext.writeAndFlush(new ResponseDTO("get",Server.cache.get(commandDTO.getKey())).toBytes());
                break;
            case "put":
                channelHandlerContext.writeAndFlush(new ResponseDTO("put",Server.cache.put(commandDTO.getKey(),commandDTO.getValue())).toBytes());
                break;
            case "remove":
                channelHandlerContext.writeAndFlush(new ResponseDTO("remove",Server.cache.remove(commandDTO.getKey())).toBytes());
                break;
            case "getMaxMemorySize":
                channelHandlerContext.writeAndFlush(new ResponseDTO("getMaxMemorySize",Server.cache.getMaxMemorySize()).toBytes());
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + commandDTO.getType());
        }
    }
}
