package com.barofarm.support.notification.domain;

// 알림타입 enum
// - UI / 도메인에서 의미 공유
public enum NotificationType {
    ORDER_CREATED,
    ORDER_PAID,
    DELIVERY_STARTED,
    DELIVERY_DONE,
    REVIEW_REQUESTED
}
