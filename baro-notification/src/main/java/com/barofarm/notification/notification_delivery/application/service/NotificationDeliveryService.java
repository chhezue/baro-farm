package com.barofarm.notification.notification_delivery.application.service;

import com.barofarm.notification.notification_delivery.application.port.EmailSenderPort;
import com.barofarm.notification.notification_delivery.application.port.PushSenderPort;
import com.barofarm.notification.notification_delivery.application.port.RecipientResolverPort;
import com.barofarm.notification.notification_delivery.domain.event.NotificationEventPayload;
import com.barofarm.notification.notification_delivery.domain.model.DeliveryChannel;
import com.barofarm.notification.notification_delivery.domain.model.RecipientProfile;
import com.barofarm.notification.notification_delivery.infrastructure.util.IdempotencyStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 알림 전달 유스케이스의 핵심 서비스.
 * 이벤트를 기준으로 채널(EMAIL/PUSH)을 결정하고 실제 발송을 수행한다.
 *
 * [기본 설명]
 * - Producer(notification 도메인)는 인앱 저장을 담당한다.
 * - DeliveryService는 외부 채널 발송만 담당한다.
 *
 */

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final RecipientResolverPort recipientResolver;
    private final EmailSenderPort emailSender;
    private final PushSenderPort pushSender;
    private final IdempotencyStore idempotencyStore;

    public void deliver(NotificationEventPayload payload) {

        // eventId 기준 멱등 처리: 이미 처리된 이벤트는 즉시 종료
        if (!idempotencyStore.tryMarkProcessed(payload.eventId())) {
            return;
        }

        RecipientProfile profile = recipientResolver.resolve(payload.recipientUserId());

        Set<DeliveryChannel> channels = parseChannels(payload.channels());

        // 이메일 채널 발송
        if (channels.contains(DeliveryChannel.EMAIL)) {
            if (profile.email() == null || profile.email().isBlank()) {
                throw new IllegalStateException("Recipient email missing");
            }
            emailSender.send(profile.email(), payload.title(), payload.body());
        }

        // 푸시 채널 발송
        if (channels.contains(DeliveryChannel.PUSH)) {
            if (profile.fcmToken() == null || profile.fcmToken().isBlank()) {
                throw new IllegalStateException("Recipient fcmToken missing");
            }
            pushSender.send(profile.fcmToken(), payload.title(), payload.body(), payload.deepLink());
        }

    }

    private Set<DeliveryChannel> parseChannels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<DeliveryChannel> channels = new HashSet<>();
        for (String ch : raw) {
            if (ch == null) {
                continue;
            }
            try {
                channels.add(DeliveryChannel.valueOf(ch.trim()));
            } catch (Exception e) {
            }
        }

        channels.remove(DeliveryChannel.IN_APP);
        return channels;
    }

}
