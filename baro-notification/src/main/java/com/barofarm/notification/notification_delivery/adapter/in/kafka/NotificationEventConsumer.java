package com.barofarm.notification.notification_delivery.adapter.in.kafka;

import com.barofarm.notification.notification_delivery.application.handler.NotificationEventHandler;
import com.barofarm.notification.notification_delivery.domain.event.NotificationEventPayload;
import com.barofarm.notification.notification_delivery.infrastructure.util.Jsons;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 알림 전달 이벤트용 Kafka Consumer.
 * 처리 성공 시에만 수동 ack를 수행하고, 실패 시 예외를 재전파한다.
 */

@Slf4j
@Component
@Profile("!mock & !local-mail")
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationEventHandler handler;

    @KafkaListener(
        topics = "${notification.delivery.kafka.topic:notification-events}",
        groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String json = record.value();

            NotificationEventPayload payload = Jsons.fromJson(json, NotificationEventPayload.class);

            handler.handle(payload);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Notification delivery failed. key={}, offset={}, err={}",
                record.key(), record.offset(), e.getMessage(), e);
            throw e;
        }
    }
}
