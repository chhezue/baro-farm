package com.barofarm.support.experience.infrastructure.kafka;

import com.barofarm.support.experience.event.ExperienceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Experience 이벤트를 Kafka로 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceEventProducer {

    private final KafkaTemplate<String, ExperienceEvent> kafkaTemplate;

    private static final String TOPIC = "experience-events"; // 토픽 이름

    public void send(ExperienceEvent event) {
        ExperienceEvent.ExperienceEventData data = event.getData();
        log.info(
            "📤 [PRODUCER] Sending experience event to topic '{}' - Type: {}, ID: {}, Name: {}, Price: {}",
            TOPIC, event.getType(), data.getExperienceId(), data.getExperienceName(),
            data.getPricePerPerson());
        kafkaTemplate.send(TOPIC, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ [PRODUCER] Sent to '{}' - Type: {}, ID: {}, Partition: {}, Offset: {}",
                    TOPIC, event.getType(), data.getExperienceId(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            } else {
                log.error("❌ [PRODUCER] Failed to send to '{}' - Type: {}, ID: {}, Error: {}",
                    TOPIC, event.getType(), data.getExperienceId(), ex.getMessage(), ex);
            }
        });
    }
}
