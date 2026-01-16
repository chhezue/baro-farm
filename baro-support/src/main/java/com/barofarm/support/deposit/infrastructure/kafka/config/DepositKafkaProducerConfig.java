package com.barofarm.support.deposit.infrastructure.kafka.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Deposit 도메인 Kafka Producer 설정
 * - Outbox Publisher 에서 사용하는 String 메시지 발행
 * - RetryableTopic 에서 사용할 기본 KafkaTemplate 역할도 수행
 */
@Configuration
@EnableKafka
public class DepositKafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> depositProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * 기본 KafkaTemplate
     * - DepositOutboxPublisher 에서 주입받아 사용
     * - RetryableTopic 이 참조하는 defaultRetryTopicKafkaTemplate 으로도 등록
     */
    @Bean(name = {"depositKafkaTemplate", "defaultRetryTopicKafkaTemplate"})
    public KafkaTemplate<String, String> depositKafkaTemplate() {
        return new KafkaTemplate<>(depositProducerFactory());
    }
}

