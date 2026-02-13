package com.barofarm.notification.notification_delivery.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka Consumer ?ㅼ젙
 *
 * 紐⑺몴:
 * - enable-auto-commit=false
 * - 硫붿떆吏 泥섎━ ?깃났 ?쒕쭔 ack.acknowledge()
 * - ?ㅽ뙣 ??ErrorHandler媛 ?ъ떆????DLQ
 *
 * 二쇱쓽:
 * - spring.kafka.* ?띿꽦?쇰줈???遺遺??ㅼ젙 媛?ν븯吏留?
 * - "AckMode", "ErrorHandler" 媛숈? ?듭떖 ?댁쁺 ?듭뀡? Java Config濡?紐낆떆?섎뒗 寃?醫뗫떎.
 */
@Configuration
@Profile("!local & !mock & !local-mail")
public class NotificationKafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties props) {
        Map<String, Object> config = new HashMap<>(props.buildConsumerProperties());

        // 臾몄옄??JSON??諛쏆쓣 寃껋씠誘濡?StringDeserializer
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // auto commit 湲덉?(?뺥솗??泥섎━ 蹂댁옣)
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        CommonErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // ?섎룞 Ack 紐⑤뱶: ?깃났 ?쒖뿉留?而ㅻ컠??
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // ?뚮┝ ?꾩슜 ?먮윭 ?몃뱾???ъ떆??DLQ): KafkaErrorHandlerConfig.defaultErrorHandler
        factory.setCommonErrorHandler(errorHandler);

        // concurrency???뚰떚???섏뿉 留욎떠 議곗젅
        // factory.setConcurrency(2);

        return factory;
    }
}
