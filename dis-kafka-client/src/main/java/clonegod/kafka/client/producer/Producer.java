package clonegod.kafka.client.producer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import clonegod.kafka.client.conf.KafkaProperties;

public class Producer extends Thread {
    private final KafkaProducer<Integer, String> producer;
    private final String topic;
    private final Boolean isAsync;

    public Producer(String topic, Boolean isAsync) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaProperties.KAFKA_SERVER_URL_LIST); // 客户端连接kafka集群的地址 
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "DemoProducer"); // 生产者的唯一标识
        /**
         * 消息的确认模式（生产者关心消息是否发送成功）：
         * acks=0 ：消息被添加到生产者客户端的tcp socket buffer即认为发送成功（消息还没有真正发送到broker上），丢失的可能性较高；
         * acks=1 ：broker leader接收消息并写入本地log就立即响应成功；
         * acks=all / acks=-1 ：broker leader需要与ISR集合中所有的follower都同步数据完成后才返回成功，可靠性最好，效率低，也可能丢失数据
         */
        props.put(ProducerConfig.ACKS_CONFIG, "all"); 
        props.put(ProducerConfig.RETRIES_CONFIG, 0); // 重试次数
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 每个partition都会分配一个buffer缓冲区，该值为每个缓冲区可缓存的消息上限（bytes），当达到上限将立即发送
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1); // 消息发送延迟毫秒数，避免大量小数据包的发送，延迟一定时间有利于缓冲更多消息后一起批量发送，提高效率
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 生产者用于缓冲消息的最大可用内存上限（对所有partition进行消息缓存的内存使用上限）
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000); // 如果缓冲区满之后，producer继续send消息，将发生阻塞，如果超过则抛出超时异常
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024*1024); // 单位字节，用于控制每次向broker批量提交消息的大小，默认为1M，即每次向broker批量提交的消息大小不能超过1M
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.IntegerSerializer"); // 设置key转换为byte[]的转换器
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");// 设置value转换为byte[]的转换器
        producer = new KafkaProducer<>(props);
        this.topic = topic;
        this.isAsync = isAsync;
    }

    public void run() {
        int messageNo = 1;
        while (true) {
            String messageStr = "Message_" + messageNo;
            long startTime = System.currentTimeMillis();
            if (isAsync) { // Send asynchronously
                producer.send(new ProducerRecord<>(topic,
                    messageNo,
                    messageStr), new SendMsgCallBack(startTime, messageNo, messageStr));
            } else { // Send synchronously
                try {
                	RecordMetadata recordMetadata = 
                    producer.send(new ProducerRecord<>(topic,
                        messageNo,
                        messageStr)).get();
                	System.out.println(String.format("Send message:(%s, %s), topic=%s, partition=%s, offset=%s", 
                			messageNo, messageStr, recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset()));
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            ++messageNo;
            try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
    }
    
	public static void main(String[] args) {
		boolean isAsync = args.length == 0 || !args[0].trim().equalsIgnoreCase("sync");
		Producer producerThread = new Producer(KafkaProperties.TOPIC, isAsync);
		producerThread.start();
	}
}

