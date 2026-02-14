package com.barofarm.notification.notification_delivery.domain.model;

/**
 * 알림 전달 채널 타입.
 * IN_APP은 저장 채널이고 EMAIL/PUSH는 외부 발송 채널이다.
 */

public enum DeliveryChannel {
    IN_APP,
    EMAIL,
    PUSH
}
