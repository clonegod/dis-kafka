## Kafka消息的文件存储机制 - 分片存储、冗余副本

#####高性能/高吞吐量：
	分片存储：同一个topic的消息分散到多个partition上存储，减轻了单机存储的压力，同时也提高了数据容量的上限
	高效写：数据顺序写磁盘，顺序IO比随机IO快很多倍
	高效读：日志分段 + 日志offset索引快速定位数据

#####高可用：
	数据冗余副本：每个partition都可以设置若干个副本节点。

### 1、 Kafka消息的文件存储机制
在kafka文件存储中，同一个topic下有多个不同的partition，每个partition为一个目录，partition的名称规则为：topic名称+有序序号，第一个序号从0开始，最大的序号为partition数量减1，partition是实际物理上的概念，而topic是逻辑上的概念。



#### Topic逻辑和partition的关系
Topic是用于存储消息的逻辑统称概念，可以看作一个消息集合。

每个topic可以有多个生产者向其推送消息，也可以有任意多个消费者消费其中的消息。

每个topic可以划分多个分区（每个Topic至少有一个分区），同一topic下的不同分区包含的消息是不同的。

每个消息在被添加到分区时，都会被分配一个offset（偏移量），它是消息在此分区中的唯一编号。

kafka通过offset保证消息在分区内的顺序，offset的顺序不跨分区，即kafka只保证在同一个分区内的消息是有序的；


Partition是以文件的形式存储在文件系统中，存储在kafka-log目录下，命名规则是：
	
	<topic_name>-<partition_id>

![](img/kafka-topic-partion.png)


##### partition还可以细分为segment，这个segment是什么呢？

假设kafka以partition为最小存储单位，那么我们可以想象当kafka producer不断发送消息，必然会引起partition文件的无限扩张，这样对于消息文件的维护以及被消费的消息的清理带来非常大的挑战，所以kafka 以segment为单位又把partition进行细分。每个partition相当于一个巨型文件被平均分配到多个大小相等的segment数据文件中（每个setment文件中的消息不一定相等），这种特性方便已经被消费的消息的清理，提高磁盘的利用率。

##### segment file组成：
由2大部分组成，分别为index file和data file，此2个文件一一对应，成对出现，后缀".index"和“.log”分别表示为segment索引文件、数据文件。

##### segment文件命名规则：
partion全局的第一个segment从0开始，后续每个segment文件名为上一个segment文件最后一条消息的offset值。数值最大为64位long大小，19位数字字符长度，没有数字用0填充。	

![](img/kafka-storage-segment.png)

##### 高效的数据查找方式
	index -> offset -> log

以上图为例，读取offset=170418的消息，首先查找segment文件，其中00000000000000000000.index为最开始的文件，第二个文件为00000000000000170410.index（起始偏移为170410+1=170411），而第三个文件为00000000000000239430.index（起始偏移为239430+1=239431），所以这个offset=170418就落到了第二个文件之中。

其他后续文件可以依次类推，以起始偏移量命名并排列这些文件，然后根据二分查找法就可以快速定位到具体文件位置。

其次根据00000000000000170410.index文件中的[8,1325]定位到00000000000000170410.log文件中的1325的位置进行读取。


**日志按大小进行分段存储（日志滚动），好处是：便于日志的管理与压缩，还可以提高消息检索速度。**

	稀疏索引 + 控制单个日志文件大小 =》提高文件检索的效率

两个重要到文件：

	原始消息日志	00000****.log
	原始消息日志的索引文件  00000****.index

初始日志，从00000000000000开始编号
![](img/kafka-storage-1.png)

随着消息的不断堆积，日志累计过程中会发生分段，于是生成新的日志文件
![](img/kafka-storage-2.png)


###  2、高可用副本机制 - leader election
![](img/kafka-replicas.png)

在kafka引入replication机制以后，同一个partition会有多个Replica。

那么在这些replication之间需要选出一个Leader，Producer或者Consumer只与这个Leader进行交互，其他的Replica作为Follower从leader中复制数据（因为需要保证一个Partition中的多个Replica之间的数据一致性，其中一个Replica宕机以后其他的Replica必须要能继续提供服务且不能造成数据重复和数据丢失）。

如果没有leader，所有的Replica都可以同时读写数据，那么就需要保证多个Replica之间互相同步数据，数据一致性和有序性就很难保证，同时也增加了Replication实现的复杂性和出错的概率。
在引入leader以后，leader负责数据读写，follower只向leader顺序fetch数据，简单而且高效。

##### 如何将所有的Replica均匀分布到整个集群

为了更好的做到负载均衡，kafka尽量会把所有的partition均匀分配到整个集群上。如果所有的replica都在同一个broker上，那么一旦broker宕机所有的Replica都无法工作。kafka分配Replica的算法

	1. 把所有的Broker（n）和待分配的Partition排序
	2. 把第i个partition分配到 （i mod n）个broker上
	3. 把第i个partition的第j个Replica分配到 ( (i+j) mod n) 个broker上


##### 如何处理所有的Replica不工作的情况
在**ISR集合**中至少有一个follower时，Kafka可以确保已经commit的数据不丢失，但如果某个Partition的所有Replica都宕机了，就无法保证数据不丢失了。

	1. 等待ISR中的任一个Replica“活”过来，并且选它作为Leader ---- 一致性
	2. 选择第一个“活”过来的Replica（不一定是ISR中的）作为Leader  ---- 可用性

这就需要在可用性和一致性当中作出一个简单的折衷。

如果一定要等待ISR中的Replica“活”过来，那不可用的时间就可能会相对较长。而且如果ISR中的所有Replica都无法“活”过来了，或者数据都丢失了，这个Partition将永远不可用。

选择第一个“活”过来的Replica作为Leader，而这个Replica不是ISR中的Replica，那即使它并不保证已经包含了所有已commit的消息，它也会成为Leader而作为consumer的数据源（前文有说明，所有读写都由Leader完成）。

Kafka0.8.*使用了第二种方式。
Kafka支持用户通过配置选择这两种方式中的一种，从而根据不同的使用场景选择高可用性还是强一致性。


#### 副本同步细节分析 - ISR（副本同步队列）
ISR：ISR表示目前**“可用且消息量与leader相差不多”**的副本集合，这是整个副本集合的一个子集。
维护的是有资格提升为leader的follower节点。

	1. 副本的所有节点都必须要和zookeeper保持连接状态。
	2. 副本的最后一条消息的offset和leader副本的最后一条消息的offset之间的差值不能超过指定的阀值，这个阀值是可以设置的（replica.lag.max.messages）。


###### HW&LEO --- HW(High Watermark)和LEO(Log End Offset)

关于follower副本同步的过程中，还有两个关键的概念，**HW(HighWatermark)**和**LEO(Log End Offset)**. 这两个参数跟ISR集合紧密关联。

**HW标记了一个特殊的offset，当消费者处理消息的时候，只能拉去到HW之前的消息，HW之后的消息对消费者来说是不可见的。**也就是说，取partition对应ISR中最小的LEO作为HW，consumer最多只能消费到HW所在的位置。

每个replica都有HW，leader和follower各自维护更新自己的HW的状态。
对于leader新写入的消息，consumer不能立刻消费，leader会等待该消息被所有ISR中的replicas同步更新HW，此时消息才能被consumer消费。这样就保证了如果leader副本损坏，该消息仍然可以从新选举的leader中获取。

----这里引申出一个结论：如果设置的副本数太多，消息被consumer消费的时效性就会降低！！！

**LEO 是所有副本都会有的一个offset标记，它指向追加到当前副本的最后一个消息的offset。**

当生产者向leader发送新的消息的时候，leader副本的LEO标记就会递增；当follower副本成功从leader副本拉去消息并更新到本地的时候，follower副本的LEO就会增加。


副本消息复制过程中，通过hw和leo来标记消息在各个副本中的状态：

![](img/kafka-replica-hw-leo.png)

---

## kafka的日志策略

#### 日志清除策略
无论消费者是否已经消费了消息，kafka都会一直保存这些消息，但并不会像数据库那样长期保存。

为了避免磁盘被占满，kafka会配置相应的保留策略（retention policy），以实现周期性地删除陈旧的消息。

日志的分段存储，一方面能够减少单个文件内容的大小，另一方面，方便 kafka 进行日志清理。

kafka会启动一个后台线程，定期检查是否存在可以删除的消息。

日志的清理策略有两个：

	1. 根据消息的保留时间，当消息在 kafka 中保存的时间超过了指定的时间，就会触发清理过程
	
	2. 根据 topic 存储的数据大小，当 topic 所占的日志文件大小大于一定的阀值，则可以开始删除最旧的消息。

通过 log.retention.bytes 和 log.retention.hours 这两个参数来设置，当其中任意一个达到要求，都会执行删除。

默认的保留时间是：7 天


#### 日志压缩策略
Kafka 还提供了“日志压缩（Log Compaction）”功能，通过这个功能可以有效的减少日志文件的大小，缓解磁盘紧张的情况。

在很多场景中，消息的key与value的值之间的对应关系是不断变化的，就像数据库中的数据会不断被修改一样，消费者只关心key对应的最新的value。我们可以开启日志压缩功能，kafka定期将相同key的消息进行合并，只保留最新的value值。


如下图所示，相同key的消息被压缩后，相同key的消息仅保留最新的。
![](img/kafka-log-compress.png)

---
## kafka的高吞吐量的因素

##### 1. 顺序写的方式存储数据 ； 

##### 2. 消息的批量发送；
在异步发送模式中。kafka允许进行批量发送，也就是producer先将消息缓存到内存中，然后一次请求批量推送到broker。

两个控制参数：

	batch.size 每批次发送的数据大小
	linger.ms  间隔时间

这样减少了磁盘频繁io以及网络IO造成的性能瓶颈

##### 3. 向客户端发送消息时，数据的零拷贝优化 - java  Files.transferTo(...)
消息从发送到落地保存，broker维护的消息日志本身就是文件目录，每个文件都是二进制保存，生产者和消费者使用相同的格式来处理。

在消费者获取消息时，服务器先从硬盘读取数据到内存，然后把内存中的数据原封不懂的通过socket发送给消费者。

虽然这个操作描述起来很简单，但实际上经历了很多步骤

> 常规文件拷贝
	
	▪ 操作系统将数据从磁盘读入到内核空间的页缓存
	▪ 应用程序将数据从内核空间读入到用户空间缓存中
	▪ 应用程序将数据写回到内核空间到socket缓存中
	▪ 操作系统将数据从socket缓冲区复制到网卡缓冲区，以便将数据经网络发出
![](img/file-copy.png)


>**零拷贝技术**
>
通过“零拷贝”技术可以去掉这些没必要的数据复制操作，同时也会减少上下文切换次数
零拷贝的前提：
应用程序不需要对数据进行修改，仅仅是将磁盘上的文件原封不动的发送给其它网络节点。
![](img/file-zero-copy.png)
