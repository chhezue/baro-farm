package com.barofarm.notification.notification_delivery.domain.model;

// ?섏떊?먯뿉寃??꾩넚?섍린 ?꾪빐 ?꾩슂??理쒖냼 ?뺣낫
public record RecipientProfile(
    String userId,
    String email,
    String fcmToken
) {}
