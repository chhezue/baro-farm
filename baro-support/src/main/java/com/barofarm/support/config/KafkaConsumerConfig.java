package com.barofarm.support.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    /**
     * ErrorHandler 설정
     * - 1초 간격으로 최대 3번 재시도
     * - 재시도 실패 시 로그만 남기고 다음 메시지로 진행 (무한 루프 방지)
     */
    @Bean
    public CommonErrorHandler errorHandler() {

        // 1초 간격으로 최대 3번 재시도
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        return new DefaultErrorHandler(backOff);
    }

    /**
     * KafkaListenerContainerFactory 설정
     * ErrorHandler를 적용하여 재시도 로직 활성화
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

            factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }
}
