package com.barofarm.support.notification_delivery.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * "전송 요청" 도메인 모델
 *
 * 이벤트 payload(JSON) -> DeliveryRequest로 변환해서 처리하면 좋은 이유:
 * 1) 외부 이벤트 스키마 변경과 내부 처리 모델을 분리할 수 있음
 * 2) 전송 성공/실패 결과(DeliveryResult)를 만들기 쉬움
 * 3) idempotency key를 여기서 표준화할 수 있음
 */
public record DeliveryRequest(
    String eventId,              // 중복 처리 방지를 위한 핵심 키(가능하면 UUID)
    String recipientUserId,       // 누구에게 보내는지
    Set<DeliveryChannel> channels,// EMAIL/PUSH/IN_APP 중 무엇을 보낼지
    String title,
    String body,
    String deepLink,              // push 클릭 이동용
    Instant occurredAt            // 이벤트 발생 시각
) {}
