package com.barofarm.support.experience.infrastructure.kafka;

import com.barofarm.support.event.ReservationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventProducer {

    private final KafkaTemplate<String, ReservationEvent> kafkaTemplate;

    private static final String TOPIC = "reservation-events";

    public void send(ReservationEvent event) {
        ReservationEvent.ReservationEventData data = event.getData();
        log.info(
            "📤 [PRODUCER] Sending reservation event to topic '{}' - Type: {}, Reservation ID: {}, "
                + "Experience ID: {}, Buyer ID: {}",
            TOPIC, event.getType(), data.getReservationId(), data.getExperienceId(), data.getBuyerId());

        kafkaTemplate.send(TOPIC, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                    "✅ [PRODUCER] Successfully sent reservation event to topic '{}' - Type: {}, "
                        + "Reservation ID: {}, Partition: {}, Offset: {}",
                    TOPIC, event.getType(), data.getReservationId(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            } else {
                log.error(
                    "❌ [PRODUCER] Failed to send reservation event to topic '{}' - Type: {}, "
                        + "Reservation ID: {}, Error: {}",
                    TOPIC, event.getType(), data.getReservationId(), ex.getMessage(), ex);
            }
        });
    }
}
