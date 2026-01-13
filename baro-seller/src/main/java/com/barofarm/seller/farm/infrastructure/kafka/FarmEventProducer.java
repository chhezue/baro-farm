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
        // 파티션 키로 farmId를 사용하여 동일 farm의 이벤트 순서 보장
        String partitionKey = data.getFarmId().toString();

        log.info(
            "📤 [PRODUCER] Sending farm event to topic '{}' - Type: {}, Farm ID: {}, Seller ID: {}, Partition Key: {}",
            TOPIC, event.getType(), data.getFarmId(), data.getSellerId(), partitionKey);

        kafkaTemplate.send(TOPIC, partitionKey, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                    "✅ [PRODUCER] Successfully sent farm event to topic '{}' - Type: {}, Farm ID: {}, "
                        + "Partition: {}, Offset: {}, Partition Key: {}",
                    TOPIC, event.getType(), data.getFarmId(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), partitionKey);
            } else {
                log.error(
                    "❌ [PRODUCER] Failed to send farm event to topic '{}' - Type: {}, Farm ID: {}, "
                        + "Partition Key: {}, Error: {}",
                    TOPIC, event.getType(), data.getFarmId(), partitionKey, ex.getMessage(), ex);
            }
        });
    }
}
