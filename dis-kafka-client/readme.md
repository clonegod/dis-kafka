## Kafka 需要理解的若干问题

## kafka 支持哪些消息模型？
	点对点：1个消息最多被消费1次；
	发布订阅：1个消息可以被不同的订阅者同时消费；

## kafka 如何实现对点对消息模式？
	多个producer发送消息到同一个topic上，消费端只能设置一个group，所有消费者都属于这个组。

## kafka 如何实现发布/订阅消息模式？
	多个producer发送消息到同一个topic上，消费端通过配置不同的group实现消息的消费。不同group之间对消息的消费相互隔离的，因此每个group都可以从topic上消费到所有的消息。
	每个group内可以有多个consumer对消息进行并行消费，不同的consumer消费不同的partition，提高消息处理速度。

## kafka 如何保证消息的有序消费？
	kafka仅支持单个partition上消息的有序消费。因此，如果需要消息有序消费，可通过发送消息时设置key属性将相关消息路由到同一个partition上。
	kafka不支持消息的全局有序，也就是说kafka不能保证消息在不同partition上有序消费。如果一定要保证全局有序，那只能将topic的分区数设置为1，这样就能保证消息全局有序了。
	

## kafka 根据partition的个数来设置consumer的个数？
	一个topic下有若干个分区partition，每个partition只能被1个consumer消费。
	因此，作为消费端系统，需要根据topic的partition个数来设置consumer的数量。
	比如，topicA有5个分区，那么最多可以有5个consumer来消费topicA上的数据。
	更多的consumer则是浪费，因为每个partition只会被唯一1个consumer消费。

	1个consumer可以消费多个partition。
	1个partition只能被相同group下的1个consumer消费。
	

## kafka 消息保存时间是多久？性能会随着消息数增长而降低吗？
	kafka 消息存储有效期可通过配置`retention period`参数进行设置。
	比如，设置`log.retention`为2天，则2天后消息将从broker上删除，释放磁盘空间。
	Kafka的性能在数据大小方面实际上是恒定的，因此长时间存储数据不是问题（只要服务器磁盘空间扛得住）。
	
	log.retention.bytes = -1 （按容量控制，默认：无限制）
	log.retention.hours = 168 （按存储时间空间，默认：7天）

## kafka 怎样维护各个consumer在partition上的消费进度？
	消息的消费进度由consumer维护，通过对应partition上的offset来维护消息的消费进度。
	因此，对客户端而言，它可以重置offset来重新消费消息，也可以跳过一些消息来消费最近的消息，或者从now开始消费消息。
	
	
	
##  （Distribution）kafka 消息分区存储的好处有哪些？
	将topic划分为不同的partition分散到不同的节点上，实现了kafka集群存储的线性可扩展；
	消息分区后，每个分区可由不同的consumer进行并行消费，提高了topic消息的消费速度；
	消息分区后，结合副本机制，可实现集群的容错功能。
	
## kafka partition的leader 与 follower？
	partition的leader负责该partition上的读写操作，follower被动从leader同步数据。
	1个leader可以配置多个follower。
	isr集合维护与leader保持同步的follower。
	当某个partition的leader所在节点宕机后，kafka将自动从该leader的isr集合中选出一个follower提升为leader。
	
## kafka 可以不使用 zookeeper吗？
	Broker使用zookeeper来注册broker信息,以及监测partition leader的存活性.
	
	
## （Producers）	producer将消息发送到不同partition上
	producer决定将消息发布到哪个topic，以及该topic的哪个partition上。
	kafka生产者发布消息的负载均衡策略：
	1、round-robin，以轮询方式将消息发送到每个partition。
	2、根据消息的key来选择partition，因此具有相同key的消息将被发送到同一个partition。
	
## （Consumers）consumer 负载均衡消费消息
	consumer必须将自己标识到一个group中。
	group理解为一组逻辑上的订阅者。group内可以有多个consumer，实现扩展性和容错性。
	1、当有consumer不可用，它所消费的partition将自动分配给其它consumer处理。
	2、当有新的consumer加入到group，它将从其它consumer那里接管一部分partition来进行消费。
	
	kafka通过group对消息进行负载均衡：
	1、对同一个group中的消费者，消息将在这些consumer中进行负载均衡消费；
	2、对不同group中的消费者，消息将被广播给所有这些group，不同group之间的消息消费互不影响；
	

	

	



