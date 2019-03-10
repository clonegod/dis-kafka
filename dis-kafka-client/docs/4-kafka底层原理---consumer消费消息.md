# kafka消息消费模式 - 基于consumerGroup 实现（队列、主题）的消费场景

---
## 消费端关键的配置参数
	【kafka集群的地址】
		> brokerlist	配置Kafka集群的连接地址

	【消费组】
		> group.id		消费者属于的组  

	【反序列化的参数】
		> key.deserializer		消息的key的反序列化方式
		> value.deserializer	消息的value的反序列化方式

	【消费确认模式】
		> enable.auto.commit		消息是否自动确认（true，自动确认； false，手动确认）
		> auto.commit.interval.ms	消息自动批量提交的时间间隔（自动提交模式时才有效）

	【心跳超时时间】
		> session.timeout.ms		消费端与broker保持心跳的超时时间，如果发生超时，broker将移除此consumer，并执行rebalance进行消息消费的再平衡。

	【Offset重置策略】
		> auto.offset.reset	消息的偏移量策略
		earliest	从最老的那条消息开始消费
		latest		从最新的那条消息开始消费
		none		如果所属的consumer group没有offset，抛出异常

	【批量】
		> max.poll.records	每次从broker上拉取消息的最大条数

### 消费端获取消息的方式
	kafka消费端都采用poll拉取的方式从broker上获取消息

### 消费端对消息消费的确认模式
	自动确认		消费端接收到消息即认为处理成功，会被自动commit提交。

	手动确认		在相关业务操作成功后，才对该消息进行确认！如果业务操作失败，则不进行确认。


---

## Kafka的消息的消费原理

### broker如何保存消费端的消费位置 ?
broker会默认创建50个 __consumer_offsets_N 个目录，用来存储consumer消费的offset

消息offset是根据consumerGroup进行计算并维护到某个目录中

###### 计算__consumer_offset_N： 

	hashcode('consumerGroupName') % 50 // 默认创建50个__consumer_offset目录

	System.out.println(Math.abs("DemoConsumer".hashCode()) % 50); // 17

	# 查看groupName='DemoConsumer'的消费端的offset信息
	> kafka-simple-consumer-shell.bat --topic __consumer_offsets --partition 17 --broker-list 127.0.0.1:9092 --formatter "kafka.coordinator.group.GroupMetadataManager$OffsetsMessageFormatter"
	
	# 以下日志记录了： “哪个消费组”在“哪个Topic的哪个分区”上的消息偏移量
	[DemoConsumer,myTopic,0]::[OffsetMetadata[3,NO_METADATA],CommitTime 1540134678640,ExpirationTime 1540221078640]
	[DemoConsumer,myTopic,1]::[OffsetMetadata[3,NO_METADATA],CommitTime 1540134678640,ExpirationTime 1540221078640]
	[DemoConsumer,myTopic,2]::[OffsetMetadata[3,NO_METADATA],CommitTime 1540134678640,ExpirationTime 1540221078640]

##### kafka历史版本-使用zk记录consumer group的offset偏移信息
之前Kafka存在的一个非常大的性能隐患就是利用ZK来记录各个Consumer Group的消费进度（offset）。当然Client帮我们自动做了这些事情，但是Consumer需要和ZK频繁交互，而利用ZK Client API对ZK频繁写入是一个低效的操作，并且从水平扩展性上来讲也存在问题。所以ZK抖一抖，集群吞吐量就跟着一起抖，严重的时候简直抖的停不下来。

##### kafka新版本-使用kafka内部日志文件保存offset偏移信息
新版Kafka已推荐将consumer的位移信息保存在Kafka内部的topic中，即__consumer_offsets topic。__consumer_offsets_topic默认有50个分区，分区个数可以调整。


**__consumer_offsets_N 维护consumer group的消费偏移量**

通过以下操作来看看__consumer_offsets_topic是怎么存储每个Group的消费进度：

	1. 计算consumer group对应的hash值

![](img/kafka-group-hash.png)

	2. 获得consumer group的位移信息
	bin/kafka-simple-consumer-shell.sh --topic __consumer_offsets --partition 17 -broker-list 192.168.1.201:9092,192.168.1.202:9092,192.168.1.203:9092 --formatter kafka.coordinator.group.GroupMetadataManager\$OffsetsMessageFormatter


#### 分区分配策略
1个Topic下存在多个分区，1个consumer group内存在若干个consumer，consumer根据什么规则消费消息？

	1、consumer group内只有1个consumer，则所有分区上的消息都由该consumer进行消费。
	2、consumer group内consumer的个数与分区数相同，则每个consumer消费1个分区上的消息。
	3、consumer group内consumer的个数大于分区数，则会造成多余的consumer空闲，浪费资源。

---
![](img/kafka-partiion-comsume.png)	

## 问：相同Group下的多个consumer如何分配partition上的数据？
在kafka中每个topic一般都会有很多个partitions。
为了提高消息的消费速度，我们可能会启动多个consumer去消费； 
同时，kafka存在consumer group的概念，也就是group.id一样的consumer，这些consumer属于一个consumer group，组内的所有消费者协调在一起来消费消费订阅主题的所有分区。

那么同一个consumer group里面的consumer是怎么去分配该消费哪个分区里的数据？
这个就涉及到了kafka内部**分区分配策略（Partition Assignment Strategy）**

#### 在 Kafka 内部存在两种分区分配策略：
Range（默认） 和 RoundRobin。

通过：partition.assignment.strategy指定，默认是range策略。也可以指定分区分配策略。

##### 两种分区分配策略
---kafka将根据以下两种策略来对partition与consumer绑定关系。

	1、Range 策略（默认）
	0 ，1 ，2 ，3 ，4，5，6，7，8，9
	算法：10(partition num/3(consumer num) =3
	c0 [0...3] 	按范围将分区指派给consumer，因此编号从0到3的partition分配给了c0消费者
	c1 [4...6] 
	c2 [7...9]

#

	2、Roundrobin 策略
	0 ，1 ，2 ，3 ，4，5，6，7，8，9
	算法：按分配编号轮询分配。
	c0  [0,3,6,9]   按轮询策略进行分配，因此c0消费者被依次分配了编号为0,3,6,9的partition
	c1  [1,4,7]
	c2  [2,5,8]

## consumer rebalance 机制 （重要）
kafka会实时根据consumer group中消费者的个数的变化，或者partition的变化，对分区消息的消费进行再平衡，保持partition上的消息可以被高效的消费。

当以下事件发生时，Kafka 将会进行一次分区分配的更新：

	1. 同一个consumer group内新增了消费者
	2. 消费者离开当前所属的consumer group，包括shuts down 或crashes
	3. 订阅的主题新增分区（分区数量发生变化）
	4. 消费者主动取消对某个topic的订阅
	5. 也就是说，把分区的所有权从一个消费者移到另外一个消费者上，这个是kafka consumer 的rebalance机制。如何rebalance就涉及到前面说的分区分配策略。


#### partition 和 consumer 的对应关系的分配问题  ->  GroupCoordinator
这个分配是由一个叫 GroupCoordinator 负责的。 

GroupCoordinator 其实就是一台 Broker。

每个 consumer group 都有自己的 GroupCoordinator ，当 consumer 开始消费的时候需要发送 GroupCoordinatorRequest 找到自己的 GroupCoordinator ，然后向这个 GroupCoordinator 发起 JoinGroup 的请求。

__consumer_offsets 也是一个 topic ，那么它就也分了 Partition ，比如他就分为 n 个 Partition，则 Coordinator的选择方法就是 leader(abs(group.hashcode) % n) , 也就是用 consumer group 的名字的 hashcode 对__consumer_offsets 的分区数取模，得到一个分区编号，然后这个分区的 leader 在哪台 Broker 上，哪台 Broker 就是这个Consumer Group 的 GroupCoordinator 。


---

## consumer pull
consumer消费消息时，通过offset从broker上定位消息的位置，拉取消息进行消费；

**注意： Kafka只提供了Topic的消息模型，没有提供传统Queue的消息模型。**

	使用高层API（推荐）：
	消费端按broker上记录的offset来获取消息（kafka对每个group消费消息offset进行了记录）。
	消息的offset完全由kafka来维护，客户端只需要拉取消息即可。

	使用底层API：
	消息的offset位置需要由消费端来维护，消费端告诉broker从哪个partition的哪个offset开始获取消息。
	由于复杂性较高，需要处理大量的细节，因此一般情况下不建议使用底层API。

---
##consumerGroup的设计思想：
	基于group来维护每个组的消息偏移位置offset。
	
	每个consumer都需要绑定到某个group上，即每个consumer有一个所属的group；
	
	每个group在broker上都有一个日志文件（__consumer_offset_x）来维护该group当前消息的offset；
	
	每次消费从对应group的offset位置开始消费消息；
	
	每当consumer消费成功一条消息，broker就会对group的消息偏移量offset做加1操作；



---
### 队列Queue --- 基于Topic来实现Queue 队列的功能

同一个Group下的consumer，同一个消息仅被其中某个consumer消费。

	kafka提供了partition上消息的有序消费支持，但不保障消息的全局顺序消费。

	【底层原理】
		生产者将消息发送到某个Topic上；
		Topic基于分区策略绑定到了N个partition，生产者发往该Topic的消息会被broker基于消息的key将消息路由到某个partition上进行存储（如果消息没有指定key，则默认roundrobin轮询分配消息到partition上）；
		
		消费者消费消息时，kafka会将该Topic下的partition基于range策略或round-ronbin策略，将不同的partition分配给不同的consumer进行消费，每个消费端所处理的消息是来自不同的partition的。

		如果group下只有1个consumer，则该Topic下所有partition上的消息都会分配给该consumer进行消费；

		基于上述原理，不同的consumer处理来自不同partition上的消息，同一个消息只会被同组内的唯一个consumer消费。
 
### 主题Topic --- 基于Topic来实现发布/订阅的功能
	不同group的consumer，彼此消费的消息互不影响；

	【底层原理】
	kafka会将对每个group的名称进行hash取模运算，然后根据得到的数值将group所消费的消息offset存储到__consumer_offset_x日志文件中保存。

	由于不同名称的group，其消费消息的offset是独立的，所以同一个消息可以被不同group内的consumer所消费。