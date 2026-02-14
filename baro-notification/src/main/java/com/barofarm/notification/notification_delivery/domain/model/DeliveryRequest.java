package com.barofarm.notification.notification_delivery.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * 전달 처리에 필요한 최소 입력 모델.
 * 이벤트 payload를 도메인 처리 관점으로 변환한 값 객체이다.
 */
public record DeliveryRequest(
    String eventId,
    String recipientUserId,
    Set<DeliveryChannel> channels,
    String title,
    String body,
    String deepLink,
    Instant occurredAt
) {}

