package com.barofarm.sample.kafka;

import java.util.Map;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaTestKafkaConfig {

    @Bean
    public ConsumerFactory<String, KafkaTestController.KafkaTestRequest>
            kafkaTestConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProps = properties.buildConsumerProperties();

        JsonDeserializer<KafkaTestController.KafkaTestRequest> valueDeserializer =
                new JsonDeserializer<>(KafkaTestController.KafkaTestRequest.class);
        valueDeserializer.addTrustedPackages("com.barofarm.sample.kafka");
        valueDeserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaTestController.KafkaTestRequest>
            kafkaTestListenerContainerFactory(
                    ConsumerFactory<String, KafkaTestController.KafkaTestRequest>
                            kafkaTestConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<
                        String, KafkaTestController.KafkaTestRequest>
                factory =
                        new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaTestConsumerFactory);
        return factory;
    }
}

