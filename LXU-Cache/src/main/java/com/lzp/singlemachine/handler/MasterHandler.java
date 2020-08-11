package com.lzp.singlemachine.handler;


        import com.lzp.common.protocol.CommandDTO;

        import com.lzp.singlemachine.service.ConsMesService;

        import io.netty.channel.ChannelHandlerContext;
        import io.netty.channel.EventLoop;
        import io.netty.channel.SimpleChannelInboundHandler;


        import java.util.*;


/**
 * @Author：luzeping
 * @Date: 2019/1/6 20:35
 */
public class MasterHandler extends SimpleChannelInboundHandler<CommandDTO.Command> {
    private static Map<EventLoop, Integer> eventLoopNumMap = new HashMap(32);


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, CommandDTO.Command command) {
        ConsMesService.addMessage(new ConsMesService.Message(command, channelHandlerContext), eventLoopNumMap.get(channelHandlerContext.channel().eventLoop()));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        //只有建立连接时会执行一次，所以这里对性能没什么要求
        EventLoop eventLoop = ctx.channel().eventLoop();
        if (eventLoopNumMap.get(eventLoop) == null) {
            synchronized (MasterHandler.class) {
                if (eventLoopNumMap.get(eventLoop) == null) {
                    eventLoopNumMap.put(eventLoop, eventLoopNumMap.size());
                }
            }
        }
    }
}
