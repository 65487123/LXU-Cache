# LXU-Cache

# Features
     Similar to Redis, a distributed cache middleware. The main 
     functions of redis are basically realized,Including its five 
     main data structures（string、list、hash、set、zset), cache 
     elimination, key setting expiration ime, persistence, clustering 
     and so on.
     It also provides various batch operation APIs to reduce the number 
     of network IOs and achieve the purpose of improving write cache efficiency.
     
# The purpose of writing this project
    The main purpose is to study
    1、Familiar with the various functions of redis:
    To write a distributed cache middleware similar to redis, 
    must know the various functions of redis.
    2、Familiar with redis design ideas and implementation principles：
    In the process of completing this project, I will definitely learn 
    from the implementation principle of redis. And verify through practice
    Prove why redis is designed like this (for example, why the main thread
    of the server side is designed to be single-threaded).
    3、Familiar with the use of jdk library and netty framework, increase practical experience in network programming and concurrent programming：
    Network communication is achieved through netty, through this project can 
    increase the practical experience of netty (custom Protocol, long-connection 
    heartbeat mechanism, cluster re-election master must be monitored through events, etc.).
    
# Main realization principle
## Thread model
    Because netty is used, the main thread model is the master-slave Reactor model: the master 
    Reactor receives Connection events,the slave Reactor receives business requests. Messages 
    will be put after receiving business requests from Reactor into a blocking queue, a single 
    thread consumes the messages in the queue, and after consuming a message, it will wrap 
    the returned result into a task and throw it to the IO thread for processing (Call  write 
    of channelHandler in non-io thread, the bottom layer of netty will do it). The serialization 
    protocol uses Protobuf, and uses the codec implemented by myself simplifies the serialization 
    and deserialization process and reduces unnecessary operations.
## Queue implementation
    I have implemented two high-performance blocking queues by myself. 
    1. many-to-one: lock-free design, the bottom layer is an array,And by throwing the messages of 
    different production threads into different blocks and between the head pointer and the tail pointer
    Memory filling solves the problem of false sharing. 
    2. One-to-one: The same is a lock-free design and solved for sharing.
    In the stand-alone mode, all requests will be thrown into the many-to-one blocking queue for 
    consumption by another independent thread.
    In cluster mode, all master nodes use many-to-one queues, and slave nodes use one-to-one queues


## Persistence implementation
    There are two persistent files: memory snapshot file snapshot.ser and write cache log file
    journal.txt. Writing these two files is executed by a separate single thread. Whenever processing
    When the thread requesting the queue takes out a write request from the queue, it will add the request to
    In journal.txt, when the write cache request reaches a certain number (you can configure it yourself)
    The content of journal.txt will be emptied and the snapshot file will be overwritten (this is also a task and will be lost
    It is executed in a special persistent thread, and the snapshot version number will be modified before throwing in. In the thread
    Before this task is executed, the version number at the time when the task is added is judged in the previous log task
    If it is inconsistent with the current version number, it will return directly without writing logs). When the system restarts, it will
    Read the two files of the snapshot, first restore the cache according to the snapshot, and then execute the log file line by line
    After the restoration is completed, the two files will be cleared and a new snapshot file will be generated.
    Since the expiration time of the key is recorded in another container, this system will generate two copies
    Persistent files are placed in two directories respectively. In other words, this system will generate
    The four persistent files are all executed by an independent single thread.


## Cluster principle
                 client  ▉
                        /| \ 
                       / |  \ 
                      /  |   \
                    ↙   ↓    ↘ master 
             master ▉   ▉    ▉ ➜➜➜ ▉slave
                 ↙ ↓   master  ↘      
               ▉   ▉   ↓ ↘       ▉slave        
           slave slave  ▉    ▉
                           slave slave
    The client accepts the request and maps different keys to different master nodes. 
    The master node will asynchronously send this request to all slave nodes of the master         
    after writing, and then return the result. When the master hangs up, the server will 
    automatically elect a new master, and the client will also establish a connection with 
    the new master, and the user will not perceive it.
                    
# 	How to use：
## Stand-alone mode
    1. The cluster-enabled configuration of config.properties is no
    2. Set the port used by the cache, the cache elimination strategy, the maximum number 
    of cache entries, the key expiration strategy, and the number of log entries when generating 
    a snapshot by modifying the config.properties configuration file.
    3. Compile the project and generate the jar package
    4. Throw it to the server and execute: nohup java -jar lxucache-server-1.0-SNAPSHOT.jar & 
    you can add other JVM startup parameters according to your needs
    
## Cluster mode
    1. The cluster-enabled configuration of config.properties is yes
    2. If it is the master node, the isMaster attribute in the configuration file is set to yes, if it is a slave node, masterIpAndPort
    Need to configure the ip and port of the master node, example: masterIpAndPort=127.0.0.1:4445
    3. Start the master node first. If there are persistent files under the master node's directory, the data will be restored after startup.
    4. Start the slave node. After startup, it will automatically synchronize the data of the master node to the slave node. 
    You can start multiple slave nodes.The performance of starting the slave node with the parameter -XX:-RestrictContended will be higher (
    the queue used by the slave node is solved through the official implementation of false sharing).
    Example: nohup java -XX:-RestrictContended -jar lxucache-server-1.0-SNAPSHOT.jar &
    You can add other JVM startup parameters according to your needs
    5. The client connects to the cluster through CacheClusterClient. Pass all nodes as parameters into the client construction method, 
    and the client will automatically find the master node and perform load balancing. The new master will be found automatically when 
    the master hangs, and the user has no perception. Currently, it does not support adding a new master node or deleting the original 
    master node at runtime (you can add or delete slave nodes).
    
 ##  Client source code URL
     https://github.com/65487123/LxuCache-Client
                          
   __________________________________________________________________________________________________________________________________________________________________
                          
# 功能介绍
    和Redis类似，一个分布式缓存中间件。redis的主要功能基本都实现了，
    包括其主要五种数据结构（string、list、hash、set、zset)、缓存淘汰、
    key设置过期时间、持久化、集群等等。
    并且提供了各种批量操作api来减少网络IO次数，达到提升写缓存效率目的。
# 写这个项目的目的
    主要目的就是用作学习
    1、熟悉redis各种功能：要写一个和redis类似的分布式缓存中间件，肯定得
    知道redis的各种功能。
    2、熟悉redis设计思想和实现原理：在完成这个项目的过程中，肯定会借鉴
    redis的实现原理。并且通过实践来验证redis为什么要这样设计(比如为什么
    server端主线程设计为单线程)。
    3、熟悉jdk类库、netty框架的运用，增加网络编程、并发编程的实践经验：
    网络通信是通过netty实现的，通过这个项目可以增加netty的实践经验(自定义
    协议、长连接心跳机制、cluster重新选举master得通过事件监听等等）。

# 主要实现原理
## 线程模型
    由于用到了netty，所以主体线程模型是主从Reactor模型，主Reactor接收
    连接事件，从Reactor接收业务请求。从Reactor接收业务请求后会把消息丢
    到一个阻塞队列中去，由一个单线程消费队列中的消息，消费完一个消息就会
    把返回结果包装成任务丢给iO线程处理（在非io线程中调用channelHandler
    的write方法，netty底层会这样做)。序列化协议用到了Protobuf，并且用
    自己实现的编解码器简化了序列化反序列化过程，减少了不必要的操作。
## 队列实现
    自己实现了两个高性能阻塞队列。1、多对一：无锁设计，底层是一个数组，
    并且通过把不同生产线程的消息丢到不同的块中以及头指针和尾指针之间
    进行内存填充解决了伪共享问题。2、一对一：同样是无锁设计并解决了为共享。
    单机模式下所有请求都会被丢到多对一阻塞队列中由另一个独立线程单线程消费。
    集群模式下，所有主节点用多对一队列，从节点用一对一队列


## 持久化实现
    持久化文件有两个文件：内存快照文件snapshot.ser和写缓存的日志文件
    journal.txt。写这两个文件是由一个独立线程单线程执行的。每当处理
    请求队列的线程从队列中取出一个写的请求时，就会把这个请求添加到
    journal.txt中，当取出的写缓存请求达到一定数量时（可以自己配置）
    会把journal.txt内容清空并重写快照文件（这也是一个任务，会被丢
    进专门持久化的线程中执行，丢进去之前会先修改快照版本号，在线程
    执行到这个任务之前，前面的加日志任务里判断到加任务时刻的版本号
    和当前版本号不一致，直接返回，不会去写日志）。当系统重启时，会
    读取快照这两个文件，先根据快照恢复缓存，然后一行一行执行日志文
    件里的请求，恢复完成后，会清空两个文件并生成新的快照文件。
    由于key的过期时间是记录在另一个容器中，所以这个系统会生成两份
    持久化文件，分别放在两个目录下面。也就是说，这个系统总共会生成
    四个持久化文件，都是由一个独立单线程执行的。

## 集群原理
                 client  ▉
                        /| \ 
                       / |  \ 
                      /  |   \
                    ↙   ↓    ↘ master 
             master ▉   ▉    ▉ ➜➜➜ ▉slave
                 ↙ ↓   master  ↘      
               ▉   ▉   ↓ ↘       ▉slave        
           slave slave  ▉    ▉
                           slave slave
    客户端接受请求，把不同的key映射到不同的主节点中，主节点写完会异步
    向这个主的所有从节点发送这个请求，然后返回结果。当主挂了，server
    端会自动选举新的主，客户端也会和新的主建立连接，用户无感知。
                    
# 	使用方法：
## 单机模式
    1、config.properties的cluster-enabled配置为no
    2、通过修改config.properties配置文件设置缓存使用的端口、缓存淘汰策略、
    缓存最大条目数量、key过期策略、生成快照时的日志条目数等。
    3、编译项目，生成jar包
    4、丢到服务器执行nohup java -jar lxucache-server-1.0-SNAPSHOT.jar & 
    可以根据自己需求添加其他JVM启动参数
    
## 集群模式
    1、config.properties的cluster-enabled配置为yes
    2、如果是主节点配置文件里isMaster属性设为yes，如果是从节点，masterIpAndPort
    需要配置为主节点的ip加端口,示例：masterIpAndPort=127.0.0.1:4445
    3、先启动主节点，主节点的目录下面如果有持久化文件，启动后会恢复数据。
    4、再启动从节点，启动后会自动同步主节点的数据到从节点中。可以启动多个从节点。
    启动从节点带上参数-XX:-RestrictContended性能会更高(从节点用的队列是通过官方实现解决伪共享的)。
    示例：nohup java -XX:-RestrictContended -jar lxucache-server-1.0-SNAPSHOT.jar & 
    可以根据自己需求添加其他JVM启动参数
    5、客户端通过CacheClusterClient来连接集群。把所有节点当作参数传入客户端构造方法中，
    客户端会自动找到主节点并做负载均衡。主挂了会自动找到新的主，用户无感知，目前不支持
    运行时增加新的主节点或删除原有主节点（可以增删从节点)。
    
## 客户端源码地址
    https://github.com/65487123/LxuCache-Client
