package com.barofarm.support.config;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/** Kafka Consumer 설정 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * DLQ(Dead Letter Queue)용 KafkaTemplate 생성
     * 실패한 메시지를 DLQ 토픽으로 전송하기 위해 사용
     */
    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            org.springframework.kafka.support.serializer.JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 중복 방지

        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * ConsumerFactory 생성
     * FarmEvent를 위한 설정
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            org.springframework.kafka.support.serializer.JsonDeserializer.class);
        config.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * ErrorHandler 설정
     * - 지수 백오프를 사용한 재시도 (초기 1초, 배수 2.0)
     * - 최대 1번 재시도 (maxElapsedTime으로 제한)
     * - 재시도 실패 시 DLQ(Dead Letter Queue) 토픽으로 메시지 전송
     *
     * DLQ 토픽 명명 규칙: 원본 토픽명 + ".DLQ"
     * 예: "farm-events" → "farm-events.DLQ"
     *
     * 지수 백오프 동작:
     * - 첫 번째 재시도: 1초 후
     * - 두 번째 재시도: 2초 후 (하지만 maxElapsedTime으로 제한되어 실행되지 않음)
     * - 재시도 실패 시: DLQ 토픽으로 메시지 전송
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        // DLQ Recoverer 생성: 실패한 메시지를 DLQ 토픽으로 전송
        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(dlqKafkaTemplate);

        // 지수 백오프 설정: 초기 간격 1초, 배수 2.0
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L); // 초기 간격: 1초
        backOff.setMultiplier(2.0); // 배수: 2.0 (1초 -> 2초 -> 4초 -> ...)
        backOff.setMaxInterval(10000L); // 최대 간격: 10초
        // 최대 경과 시간을 초기 간격보다 크고 두 번째 재시도 전에 끝나도록 설정 (최대 1번 재시도)
        backOff.setMaxElapsedTime(1500L); // 최대 경과 시간: 1.5초 (1번 재시도만 허용)

        // DefaultErrorHandler에 DLQ Recoverer 추가
        return new DefaultErrorHandler(dlqRecoverer, backOff);
    }

    /**
     * KafkaListenerContainerFactory 설정
     * ErrorHandler를 적용하여 재시도 로직 활성화
     *
     * 순서 보장을 위한 설정:
     * - concurrency: 1로 설정하여 파티션당 1개 스레드로 처리
     * - Producer에서 파티션 키(farmId)를 사용하므로, 동일 farm의 이벤트는 같은 파티션에 들어감
     * - 파티션 내에서는 Kafka가 자동으로 순서 보장
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> dlqKafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler(dlqKafkaTemplate));
        // 파티션당 1개 스레드로 처리하여 순서 보장
        // Producer에서 파티션 키를 사용하므로 동일 farm의 이벤트는 같은 파티션에 들어감
        factory.setConcurrency(1);
        // 배치 단위로 offset 커밋 (성능 최적화)
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.BATCH);
        return factory;
    }
}
