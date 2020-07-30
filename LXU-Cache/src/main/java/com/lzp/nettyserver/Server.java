package com.lzp.nettyserver;

import com.lzp.util.FileUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author：luzeping
 * @Date: 2020/1/6 20:23
 */
@SpringBootApplication
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int PORT;

    /**
     * 启动顺序：先去初始化 com.lzp.service.ConsMesService，初始化过程会把持久化数据恢复到内存，恢复完成后会
     * 清空文件并生成一个新的快照文件。然后初始化com.lzp.service.ExpireService，恢复key过期时间的持久化文件
     * 同样，恢复完成后会删除文件并生成新的快照。
     */
    static {
        PORT = Integer.parseInt(FileUtil.getProperty("port"));
        try {
            Class.forName("com.lzp.service.ConsMesService");
            Class.forName("com.lzp.service.ExpireService");
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(),e);
        }
    }

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(16);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1000)
                    .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ServerInitializer());
            serverBootstrap.bind(PORT).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
