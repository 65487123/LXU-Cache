package com.lzp;

import com.lzp.cluster.client.ClientService;
import com.lzp.cluster.handler.MasterServerInitializer;
import com.lzp.cluster.handler.SlaveServerInitializer;
import com.lzp.cluster.service.MasterConsMesService;
import com.lzp.cluster.service.SlaveConsMesService;
import com.lzp.cluster.service.SlaveExpireService;
import com.lzp.singlemachine.handler.ServerInitializer;
import com.lzp.singlemachine.service.ConsMesService;
import com.lzp.common.util.FileUtil;
import com.lzp.common.service.ThreadFactoryImpl;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private static Channel serverChannel;

    /**
     * 启动顺序：先去初始化 com.lzp.singlemachine.service.ConsMesService，初始化过程会把持久化数据恢复到内存，恢复完成后会
     * 清空文件并生成一个新的快照文件。然后初始化com.lzp.service.ExpireService，恢复key过期时间的持久化文件
     * 同样，恢复完成后会删除文件并生成新的快照。(单机模式下或者集群模式下的主节点)
     */
    static {
        PORT = Integer.parseInt(FileUtil.getProperty("port"));
        //单机模式下
        if (!"yes".equals(FileUtil.getProperty("cluster-enabled"))) {
            try {
                Class.forName("com.lzp.singlemachine.service.ConsMesService");
                Class.forName("com.lzp.singlemachine.service.ExpireService");
                workerGroup = new NioEventLoopGroup(ConsMesService.THREAD_NUM);
                serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1000)
                        .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ServerInitializer());
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        } else if ("yes".equals(FileUtil.getProperty("isMaster"))) {
            //集群模式主节点
            try {
                Class.forName("com.lzp.cluster.service.MasterConsMesService");
                Class.forName("com.lzp.cluster.service.MasterExpireService");
                workerGroup = new NioEventLoopGroup(MasterConsMesService.THREAD_NUM);
                serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1000)
                        .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new MasterServerInitializer());
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        } else {
            //集群模式从节点
            workerGroup = new NioEventLoopGroup(1);
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new SlaveServerInitializer());
        }
    }

    public static void main(String[] args) {
        try {
            serverChannel = serverBootstrap.bind(PORT).sync().channel();
            if ("yes".equals(FileUtil.getProperty("cluster-enabled")) && (!"yes".equals(FileUtil.getProperty("isMaster")))) {
                String[] masterIpAndPort = FileUtil.getProperty("masterIpAndPort").split(":");
                ClientService.sentFullSyncReq(masterIpAndPort[0], Integer.parseInt(masterIpAndPort[1]));
            }
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static void startMasterServer(List<Channel> slaves) {
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue(), new ThreadFactoryImpl("startMasterServer")).execute(() -> {
            try {
                //释放资源，并停止接受消息
                SlaveConsMesService.close();
                //释放监听端口，close后，主方法里就会收到通知，并调用shutdownGracefully方法。
                serverChannel.close().sync();
                //升级为主节点，初始化消息队列服务，恢复持久化文件，并把原来和其他从节点建立的连接传入
                MasterConsMesService.setSlaves(slaves);
                try {
                    Class.forName("com.lzp.cluster.service.MasterConsMesService");
                    bossGroup = new NioEventLoopGroup(1);
                    workerGroup = new NioEventLoopGroup(MasterConsMesService.THREAD_NUM);
                    serverBootstrap = new ServerBootstrap();
                    serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1000)
                            .childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new MasterServerInitializer());
                    serverChannel = serverBootstrap.bind(PORT).sync().channel();
                    serverChannel.closeFuture().sync();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    /**
     * 最多只会被调用一次，主挂了，选举为主的时候
     *
     * @param
     */
    public static void upgradeTomasterNode(List<Channel> slaves) {
        SlaveExpireService.close();
        startMasterServer(slaves);
    }

}
