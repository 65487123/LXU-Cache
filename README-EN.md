# LXU-Cache
[中文](https://github.com/65487123/LXU-Cache/blob/master/README.md)|English 
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
    4. Throw it to the server and execute: nohup java -jar lxucache-server-1.x.x.jar & 
    you can add other JVM startup parameters according to your needs
    
## Cluster mode
    1. The cluster-enabled configuration of config.properties is yes
    2. If it is the master node, the isMaster attribute in the configuration file is set to yes, if it is a slave node, masterIpAndPort
    Need to configure the ip and port of the master node, example: masterIpAndPort=127.0.0.1:4445
    3. Start the master node first. If there are persistent files under the master node's directory, the data will be restored after startup.
    4. Start the slave node. After startup, it will automatically synchronize the data of the master node to the slave node. 
    You can start multiple slave nodes.
    Example: nohup java -XX:-RestrictContended -jar lxucache-server-1.0-SNAPSHOT.jar &
    You can add other JVM startup parameters according to your needs
    5. The client connects to the cluster through CacheClusterClient. Pass all nodes as parameters into the client construction method, 
    and the client will automatically find the master node and perform load balancing. The new master will be found automatically when 
    the master hangs, and the user has no perception. Currently, it does not support adding a new master node or deleting the original 
    master node at runtime (you can add or delete slave nodes).
    
## Major changes in the new version

    1.0.1：Originally the return value was a Response object, which was serialized using protobuf. Version 1.0.1 changed the return value 
    to a single string, canceling protobuf serialization.
    1.0.1-sr1: Modify the queue used by the slave node, the official false-sharing solution is changed to memory filling by myself, 
    benefits: 1. It takes up less memory 2. No need to add startup parameters
  [Source code of client](https://github.com/65487123/LxuCache-Client)
                          
 
