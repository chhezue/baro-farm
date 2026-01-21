package com.barofarm.support.notification_delivery.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 처리 실패 시
 * - 3회 재시도 후
 * - DLQ 토픽으로 보냄
 *
 * 이유:
 * 메일 / 푸시는 외부 시스템이라 실패 가능성이 있음
 * 실패한 이벤트를 DLQ로 보내서 재처리
 * */
@Configuration
@Profile("!mock & !local-mail")
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler defaultErrorHandler(
        KafkaTemplate<String, String> kafkaTemplate,
        @Value("${notification.delivery.kafka.dlq-topic:notification-events-dlq}") String dlqTopic
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (ConsumerRecord<?, ?> record, Exception ex) -> new org.apache.kafka.common.TopicPartition(dlqTopic, 0)
        );

        // 1초 간격으로 3번 재시도 후 DLQ
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        return handler;
    }
}
