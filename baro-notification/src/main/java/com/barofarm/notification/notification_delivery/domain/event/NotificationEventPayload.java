package com.barofarm.notification.notification_delivery.domain.event;

import java.time.Instant;
import java.util.List;

/**
 * Kafka로 수신한 알림 이벤트 payload DTO.
 * Consumer가 의존하는 수신 스키마를 명확히 분리한다.
 */
public record NotificationEventPayload(
    String eventId,
    String type,
    String notificationId,
    String recipientUserId,
    List<String> channels,
    String title,
    String body,
    String deepLink,
    Instant occurredAt
) {}

