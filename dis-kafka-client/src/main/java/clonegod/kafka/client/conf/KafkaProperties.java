package clonegod.kafka.client.conf;

public class KafkaProperties {
    public static final String TOPIC = "myTopic";
    
    public static final String KAFKA_SERVER_URL_LIST = "localhost:9092";
//    public static final String KAFKA_SERVER_URL_LIST = "192.168.1.201:9092,192.168.1.202:9092,192.168.1.203:9092";
    public static final int KAFKA_PRODUCER_BUFFER_SIZE = 64 * 1024;
    public static final int CONNECTION_TIMEOUT = 10_000;
    
    public static final String CLIENT_ID = "SimpleConsumerDemoClient";

    private KafkaProperties() {}
}
