package com.barofarm.notification.notification_delivery.domain.model;

/**
 * ?대뼡 梨꾨꼸濡?諛쒖넚??寃껋씤吏
 *
 * - IN_APP: ?대? notification ?꾨찓?몄뿉??DB ??μ쑝濡?泥섎━
 * - EMAIL : SMTP 諛쒖넚
 * - PUSH : FCM 諛쒖넚
 *
* */

public enum DeliveryChannel {
    IN_APP,
    EMAIL,
    PUSH
}
