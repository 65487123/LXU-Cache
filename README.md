# LXU-Cache
    
# 功能介绍
    用Java编写的高性能分布式缓存中间件，通过netty实现网络通信。和redis类似，实现了redis的主要功能，包括其主要5种数据结构、	key过期设置、原子计数器、持久化等。

# 实现原理以及与redis的区别
    redis6.0以前,处理读写缓存事件的线程模型是单Reactor单线程的（还包括其他一些辅助线程和其他子进程），redis6.0以后IO线程可以设置为多线程，但是处理读写缓存事件还是一个线程的。可以理解为多个reactor来接收并发请求，然后把请求加入一个队列中，由一个单线程来消费这个队列的消息。而我这个缓存在这个基础上又优化了一些，这个缓存的线程模型是多个reactor接收并发请求,然后根据key把请求映射到多个队列中(队列个数可以配置,建议配置数量少于逻辑处理器个数并且为2的整数次幂)由和队列相同数量的线程并行消费这些请求。
	队列结构也是自己实现的，并行读写性能比JDK里提供的ArrayBlockingQueue高很多。
	在少客户端，少量请求的情况下性能和redis差不多，大量客户端大量请求情况下性能比redis高一点。但也高不了多少，因为性能瓶颈在网络IO上，纯内存操作其实耗不了多少时间。



# 	使用方法：
	1、编译项目，生成jar包
	2、通过修改config.properties配置文件设置缓存使用的端口、缓存淘汰策略、缓存最大条目数量、key过期策略、队列个数等。
	3、丢到服务器 执行 nohup java -jar lxucache-server-1.0-SNAPSHOT.jar &
