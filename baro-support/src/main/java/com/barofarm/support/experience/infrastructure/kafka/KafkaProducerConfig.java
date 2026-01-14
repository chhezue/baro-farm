package com.barofarm.support.experience.infrastructure.kafka;

import com.barofarm.support.event.ReservationEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/** 예약 이벤트 발행을 위한 Kafka Producer 설정 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ReservationEvent> reservationEventProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Producer 재시도 설정
        config.put(ProducerConfig.RETRIES_CONFIG, 2); // 최대 2번 재시도
        
        // ACKS 설정: 브로커로부터 받을 확인(acknowledgment) 수준
        // 옵션:
        // "0": 브로커 응답을 기다리지 않음 (가장 빠르지만 메시지 손실 위험 높음)
        // "1": Leader 파티션만 확인하면 OK (빠르고 안전, 대부분의 경우 권장)
        // "all" 또는 "-1": 모든 ISR(In-Sync Replicas)이 받을 때까지 대기 (가장 안전하지만 느림)
        // Reservation 이벤트는 알림 등에 사용되므로 "all" (메시지 손실 최소화)
        // 대부분의 경우 "1" 사용 (빠르고 안전)
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 중복 방지

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, ReservationEvent> reservationEventKafkaTemplate() {
        return new KafkaTemplate<>(reservationEventProducerFactory());
    }
}

