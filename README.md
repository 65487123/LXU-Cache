# LXU-Cache
用Java编写的缓存中间件，可以支持并发读写，通过netty实现网络通信，缓存淘汰策略只有LRU和LFU

使用方法：
1、编译项目，生成jar包
2、通过修改config.properties配置文件设置缓存使用的端口、缓存淘汰策略、缓存最大条目数量等。
2、丢到服务器 执行 nohup java -jar LXU-Cache-1.0-SNAPSHOT.jar &
