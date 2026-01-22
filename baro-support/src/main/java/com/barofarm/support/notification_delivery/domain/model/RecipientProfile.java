package com.barofarm.support.notification_delivery.domain.model;

// 수신자에게 전송하기 위해 필요한 최소 정보
public record RecipientProfile(
    String userId,
    String email,
    String fcmToken
) {}
