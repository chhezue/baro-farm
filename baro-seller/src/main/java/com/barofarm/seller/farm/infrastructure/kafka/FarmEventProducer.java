package com.barofarm.seller.farm.infrastructure.kafka;

import com.barofarm.seller.farm.event.FarmEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FarmEventProducer {

    private final KafkaTemplate<String, FarmEvent> kafkaTemplate;

    private static final String TOPIC = "farm-events";

    public void send(FarmEvent event) {
        FarmEvent.FarmEventData data = event.getData();
        log.info(
            "📤 [PRODUCER] Sending farm event to topic '{}' - Type: {}, Farm ID: {}, Seller ID: {}",
            TOPIC, event.getType(), data.getFarmId(), data.getSellerId());

        kafkaTemplate.send(TOPIC, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                    "✅ [PRODUCER] Successfully sent farm event to topic '{}' - Type: {}, Farm ID: {}, "
                        + "Partition: {}, Offset: {}",
                    TOPIC, event.getType(), data.getFarmId(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            } else {
                log.error(
                    "❌ [PRODUCER] Failed to send farm event to topic '{}' - Type: {}, Farm ID: {}, Error: {}",
                    TOPIC, event.getType(), data.getFarmId(), ex.getMessage(), ex);
            }
        });
    }
}
