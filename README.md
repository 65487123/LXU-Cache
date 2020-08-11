# LXU-Cache
    
# 功能介绍
    和Redis类似，一个分布式缓存中间件。redis的主要功能基本都实现了，包括其主要五种数据结构、缓存淘汰、key设置过期时间、持久化等等。
    并且提供了各种批量操作api来减少网络IO次数，达到提升写缓存效率目的。
# 主要实现原理
## 线程模型
    由于用到了netty，所以主体线程模型是主从Reactor模型，主Reactor接收连接事件，从Reactor接收业务请求。从Reactor
    接收业务请求后会把消息丢到一个阻塞队列中去，由一个单线程消费队列中的消息，消费完一个消息就会把返回结果包装成任务
    丢给iO线程处理（在非io线程中调用channelHandler的write方法，netty底层会这样做)。序列化协议用到了Protobuf，并且
    用自己实现的编解码器简化了序列化反序列化过程，减少了不必要的操作。
## 队列实现
    自己实现了两个高性能阻塞队列，1、多对一：无锁设计，底层是一个数组，并且通过把不同生产线程的消息丢到不同的块中以及
    头指针和尾指针之间进行内存填充解决了伪共享问题。2、一对一：同样是无锁设计并解决了为共享。
    单机模式下所有请求都会被丢到多对一阻塞队列中由另一个独立线程单线程消费。
    集群模式下，所有主节点用多对一队列，从节点用一对一队列


## 持久化实现
    持久化文件有两个文件：内存快照文件snapshot.ser和写缓存的日志文件journal.txt。写这两个文件是由一个独立线程单线程执行
    的。每当处理请求队列的线程从队列中取出一个写的请求时，就会把这个请求添加到journal.txt中，当取出的写缓存请求达到一定数量
    时（可以自己配置）会把journal.txt内容清空并重写快照文件（这也是一个任务，会被丢进专门持久化的线程中执行，丢进去之前会先
    修改快照版本号，在线程执行到这个任务之前，前面的加日志任务里判断到加任务时刻的版本号和当前版本号不一致，直接返回，不会去写
    日志）。当系统重启时，会读取快照这两个文件，先根据快照恢复缓存，然后一行一行执行日志文件里的请求，恢复完成后，会清空两个文件
    并生成新的快照文件。
    由于key的过期时间是记录在另一个容器中，所以这个系统会生成两份持久化文件，分别放在两个目录下面。也就是说，这个系统总共会生成
    四个持久化文件，都是由一个独立单线程执行的。


# 	使用方法：
## 单机模式
    1、config.properties的cluster-enabled配置为no
    2、通过修改config.properties配置文件设置缓存使用的端口、缓存淘汰策略、缓存最大条目数量、key过期策略、生成快照时的日志条目数等。
    3、编译项目，生成jar包
    4、丢到服务器 执行 nohup java -jar lxucache-server-1.0-SNAPSHOT.jar & ，可以根据自己需求添加其他JVM启动参数
    
## 集群模式
    1、config.properties的cluster-enabled配置为yes
    2、如果是主节点配置文件里isMaster属性设为yes，如果是从节点，masterIpAndPort需要配置为主节点的ip加端口,示例：masterIpAndPort=127.0.0.1:4445
    3、先启动主节点，主节点的目录下面如果有持久化文件，启动后会恢复数据。
    4、再启动从节点，启动后会自动同步主节点的数据到从节点中。可以启动多个从节点。启动从节点需要带上参数-XX:-RestrictContended(从节点用的队列是通过官
    方实现解决伪共享的)。示例：nohup java -XX:-RestrictContended -jar lxucache-server-1.0-SNAPSHOT.jar & ，可以根据自己需求添加其他JVM启动参数
    5、客户端通过CacheClientCluster来连接集群。
