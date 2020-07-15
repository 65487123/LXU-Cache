# LXU-Cache
用Java编写的高性能分布式缓存中间件，通过netty实现网络通信。和redis类似，实现了redis的主要功能，包括其主要5种数据结构、key过期设置、原子计数器、持久化等等。


使用方法：
1、编译项目，生成jar包
2、通过修改config.properties配置文件设置缓存使用的端口、缓存淘汰策略、缓存最大条目数量等、key过期策略等。
3、丢到服务器 执行 nohup java -jar lxucache-server-1.0-SNAPSHOT.jar &
