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

package com.lzp.lxucache.cluster.client;

import com.lzp.lxucache.common.protocol.CommandDTO;
import com.lzp.lxucache.common.util.FileUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Description:启动从节点，从节点会向主节点发起连接，发送全量同步请求，发完断开连接。
 * 主节点收到请求后向这个从节点发起长连接，进行全量同步，同步完成后实时增量同步。并且
 * 主节点会向原有的从节点发消息，让原有从节点向新加入的从节点建立连接
 *
 * @author: Lu ZePing
 * @date: 2020/8/11 8:55
 */
public class ClientService {
    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);
    private static final EventLoopGroup EVENT_EXECUTORS = new NioEventLoopGroup(1);
    private static final Bootstrap SERVER_BOOTSTRAP = new Bootstrap();

    static {
        SERVER_BOOTSTRAP.group(EVENT_EXECUTORS).channel(NioSocketChannel.class).handler(new ClientInitializer());
    }

    /**
     * Description ：向主节点发送全量同步请求
     **/
    public static void sentFullSyncReq(String masterIp, int masterPort) {
        try {
            Channel channel = SERVER_BOOTSTRAP.connect(masterIp, masterPort).sync().channel();
            channel.writeAndFlush(CommandDTO.Command.newBuilder().setType("fullSync").setKey(FileUtil.getProperty("port")).build());
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Description ：从节点向从节点建立长连接
     **/
    public static Channel getConnection(String ip,int port){
        try {
            return SERVER_BOOTSTRAP.connect(ip,port).sync().channel();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(),e);
        }
        return null;
    }

}
