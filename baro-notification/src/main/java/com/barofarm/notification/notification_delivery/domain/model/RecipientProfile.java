package com.barofarm.notification.notification_delivery.domain.model;

public record RecipientProfile(
    String userId,
    String email,
    String fcmToken
) {}
