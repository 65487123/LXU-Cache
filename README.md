# LXU-Cache
用Java编写的高性能分布式缓存中间件，和redis类似，实测性能比redis要高两倍以上，通过netty实现网络通信。暂时没时间实现集群以及持久化等功能

使用方法：
1、编译项目，生成jar包
2、通过修改config.properties配置文件设置缓存使用的端口、缓存淘汰策略、缓存最大条目数量等。
2、丢到服务器 执行 nohup java -jar lxucache-server-1.0-SNAPSHOT.jar &
