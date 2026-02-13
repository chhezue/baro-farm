package com.barofarm.notification.notification_delivery.infrastructure.config;

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
 * Kafka 泥섎━ ?ㅽ뙣 ??
 * - 3???ъ떆????
 * - DLQ ?좏뵿?쇰줈 蹂대깂
 *
 * ?댁쑀:
 * 硫붿씪 / ?몄떆???몃? ?쒖뒪?쒖씠???ㅽ뙣 媛?μ꽦???덉쓬
 * ?ㅽ뙣???대깽?몃? DLQ濡?蹂대궡???ъ쿂由?
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

        // 1珥?媛꾧꺽?쇰줈 3踰??ъ떆????DLQ
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        return handler;
    }
}
