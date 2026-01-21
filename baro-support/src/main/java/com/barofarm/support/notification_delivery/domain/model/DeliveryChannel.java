package com.barofarm.support.notification_delivery.domain.model;

/**
 * 어떤 채널로 발송할 것인지
 *
 * - IN_APP: 이미 notification 도메인에서 DB 저장으로 처리
 * - EMAIL : SMTP 발송
 * - PUSH : FCM 발송
 *
* */

public enum DeliveryChannel {
    IN_APP,
    EMAIL,
    PUSH
}
