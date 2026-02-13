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
 * Kafka Consumer
 *
 * [?듭떖 ?뺤콉]
 * - 硫붿떆吏瑜?泥섎━ ?깃났?섎㈃ ack.acknowledge()濡?而ㅻ컠
 * - 泥섎━ ?ㅽ뙣?섎㈃ ?덉쇅瑜??섏졇 ErrorHandler媛 DLQ濡?蹂대궡寃??섍굅???ъ떆??
 * */

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

            // String 湲곕컲 ?섏떊 -> Consumer???덉쟾?섍쾶 DTO濡?蹂??
            // Jsons : infrastructure/util ?댁뿉
            NotificationEventPayload payload = Jsons.fromJson(json, NotificationEventPayload.class);

            handler.handle(payload);

            // ?깃났 ???ㅽ봽??而ㅻ컠
            ack.acknowledge();
        } catch (Exception e) {
            // ?ш린??ack ?섎㈃ "?ㅽ뙣?덈뒗??而ㅻ컠?????섏뼱???ъ쿂由?遺덇???
            // ?곕씪???덉쇅瑜??섏졇??error handler濡??먮Ⅴ寃?
            // TODO: 吏곸젒 e.getMessage()?섎㈃ ?대? 濡쒖쭅 ?몄텧 ?꾪뿕? ?닿굔 ?⑥닚 濡쒓렇??愿쒖갖?
            log.error("Notification delivery failed. key={}, offset={}, err={}",
                record.key(), record.offset(), e.getMessage(), e);
            throw e;
        }
    }
}
