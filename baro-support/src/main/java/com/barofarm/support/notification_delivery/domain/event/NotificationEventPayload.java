package com.barofarm.support.notification_delivery.domain.event;

import java.time.Instant;
import java.util.List;

/**
 * Kafka로 들어오는 "알림 이벤트" JSON을 역직렬화하기 위한 DTO
 *
 * Producer(notification)와 Consumer(notification_delivery)가
 * 클래스를 공유하는 경우 배포/버전 충돌을 만들 수 있으므로
 * Consumer가 자신만의 DTO를 가지는게 더 안전
 * */
public record NotificationEventPayload(
    String eventId,
    String type,
    String notificationId,
    String recipientUserId,
    List<String> channels,     // ["EMAIL","PUSH"] 같은 문자열 리스트로 수신
    String title,
    String body,
    String deepLink,
    Instant occurredAt
) {}
