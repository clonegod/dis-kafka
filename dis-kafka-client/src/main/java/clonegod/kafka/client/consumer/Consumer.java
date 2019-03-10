package clonegod.kafka.client.consumer;

import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import clonegod.kafka.client.conf.KafkaProperties;
import kafka.utils.ShutdownableThread;

public class Consumer extends ShutdownableThread {
    private final KafkaConsumer<Integer, String> consumer;
    private final String topic;

    public Consumer(String topic) {
        super("KafkaConsumerExample", false);
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaProperties.KAFKA_SERVER_URL_LIST);
        // 消费端所属的组
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "DemoConsumer");
        // 自动确认模式，后台每隔一段时间向broker汇报该consumer的offset
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // 设置自动确认模式的时间间隔（kafka自动提交采用的是批量确认方式）
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        // 设置心跳时间，超过心跳时间broker将移除该consumer，并进行消息分配的再平衡
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        // 设置offset偏移量的重置策略
        /**
         * 当kafka中不存在该consumer的initial offset，或者offset所指向的消息已经不存在，从哪条消息开始消费？
         * 1、earliest：当consumer所属的group是新的，则重置offset，consumer将从最早的消息开始消费；
         * 2、latest：当consumer所属的group是新的，则从最新的消息开始消费；
         * 3、none：当consumer所属的group是新的，此时group并没有任何offset，将抛出异常---不知道该从哪条消息开始消费；
         */
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // or latest
        
        // 1次poll返回的最大消息数，默认值500 --- 吞吐量调优
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000); 
        
        // 设置key和value的反序列化对象
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.IntegerDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

        consumer = new KafkaConsumer<>(props);
        this.topic = topic;
    }

    @Override
    public void doWork() {
        consumer.subscribe(Collections.singletonList(this.topic));
        ConsumerRecords<Integer, String> records = consumer.poll(1000); // 消费端拉取消息的超时时间
        for (ConsumerRecord<Integer, String> record : records) {
            System.out.println("["+ record.partition() +"]" + 
            			"Received message: (" + record.key() + ", " + record.value() + ") at offset " + record.offset());
        }
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }
    
	public static void main(String[] args) {
		Consumer consumerThread = new Consumer(KafkaProperties.TOPIC);
		consumerThread.start();
		
		System.out.println(Math.abs("DemoConsumer".hashCode()) % 50);
		
		
	}
}
