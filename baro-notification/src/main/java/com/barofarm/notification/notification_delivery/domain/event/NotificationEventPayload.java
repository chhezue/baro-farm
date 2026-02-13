package com.barofarm.notification.notification_delivery.domain.event;

import java.time.Instant;
import java.util.List;

/**
 * Kafka濡??ㅼ뼱?ㅻ뒗 "?뚮┝ ?대깽?? JSON????쭅?ы솕?섍린 ?꾪븳 DTO
 *
 * Producer(notification)? Consumer(notification_delivery)媛
 * ?대옒?ㅻ? 怨듭쑀?섎뒗 寃쎌슦 諛고룷/踰꾩쟾 異⑸룎??留뚮뱾 ???덉쑝誘濡?
 * Consumer媛 ?먯떊留뚯쓽 DTO瑜?媛吏?붽쾶 ???덉쟾
 * */
public record NotificationEventPayload(
    String eventId,
    String type,
    String notificationId,
    String recipientUserId,
    List<String> channels,     // ["EMAIL","PUSH"] 媛숈? 臾몄옄??由ъ뒪?몃줈 ?섏떊
    String title,
    String body,
    String deepLink,
    Instant occurredAt
) {}
