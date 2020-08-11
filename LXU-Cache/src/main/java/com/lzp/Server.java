package com.lzp;

import com.lzp.cluster.client.ClientService;
import com.lzp.cluster.handler.SlaveServerInitializer;
import com.lzp.singlemachine.handler.MasterServerInitializer;
import com.lzp.singlemachine.service.ConsMesService;
import com.lzp.common.util.FileUtil;
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
 * @Date: 2019/1/6 20:23
 */
@SpringBootApplication
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int PORT;
    private static EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private static EventLoopGroup workerGroup;
    private static ServerBootstrap serverBootstrap = new ServerBootstrap();
    /**集群模式的从节点*/
    private static boolean clusterAndSlave = "yes".equals(FileUtil.getProperty("cluster-enabled")) && (!"yes".equals(FileUtil.getProperty("isMaster")));

    /**
     * 启动顺序：先去初始化 com.lzp.singlemachine.service.ConsMesService，初始化过程会把持久化数据恢复到内存，恢复完成后会
     * 清空文件并生成一个新的快照文件。然后初始化com.lzp.service.ExpireService，恢复key过期时间的持久化文件
     * 同样，恢复完成后会删除文件并生成新的快照。(单机模式下或者集群模式下的主节点)
     */
    static {
        PORT = Integer.parseInt(FileUtil.getProperty("port"));
        if (clusterAndSlave) {
            workerGroup = new NioEventLoopGroup(1);
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new SlaveServerInitializer());
        } else {
            //只有单机模式下或者集群模式下的主节点才会去恢复持久化数据
            try {
                Class.forName("com.lzp.singlemachine.service.ConsMesService");
                Class.forName("com.lzp.singlemachine.service.ExpireService");
                workerGroup = new NioEventLoopGroup(ConsMesService.THREAD_NUM);
                serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1000)
                        .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new MasterServerInitializer());
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
            }

        }
    }

    public static void main(String[] args) {
        try {
            serverBootstrap.bind(PORT).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        if (clusterAndSlave) {
            String[] masterIpAndPort = FileUtil.getProperty("masterIpAndPort").split(":");
            ClientService.sentFullSyncReq(masterIpAndPort[0], Integer.parseInt(masterIpAndPort[1]));
        }
    }
}
