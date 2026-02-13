package com.barofarm.notification.notification_delivery.application.service;

import com.barofarm.notification.notification_delivery.application.port.EmailSenderPort;
import com.barofarm.notification.notification_delivery.application.port.PushSenderPort;
import com.barofarm.notification.notification_delivery.application.port.RecipientResolverPort;
import com.barofarm.notification.notification_delivery.domain.event.NotificationEventPayload;
import com.barofarm.notification.notification_delivery.domain.model.DeliveryChannel;
import com.barofarm.notification.notification_delivery.domain.model.RecipientProfile;
import com.barofarm.notification.notification_delivery.infrastructure.util.IdempotencyStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ?뚮┝ "?꾩넚 ?좎뒪耳?댁뒪"???듭떖 ?쒕퉬??
 *
 * [湲곕낯 ?ㅻ챸]
 * - Producer(notification ?꾨찓?????몄빋 ??μ쓣 ?대떦?쒕떎.
 * - DeliveryService???몃? 梨꾨꼸 諛쒖넚留??대떦?쒕떎.
 *
 * */

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final RecipientResolverPort recipientResolver;
    private final EmailSenderPort emailSender;
    private final PushSenderPort pushSender;
    private final IdempotencyStore idempotencyStore;

    public void deliver(NotificationEventPayload payload) {

        if (!idempotencyStore.tryMarkProcessed(payload.eventId())) {
            // ?대? 泥섎━???대깽?몃㈃ 洹몃깷 醫낅즺 (以묐났 諛⑹?)
            return;
        }

        // 1) ?섏떊???꾨줈??議고쉶 (?대찓??二쇱냼, FCM ?좏겙 ??
        RecipientProfile profile = recipientResolver.resolve(payload.recipientUserId());

        // 2) ?대뼡 梨꾨꼸濡?諛쒖넚?좎?瑜?寃곗젙
        Set<DeliveryChannel> channels = parseChannels(payload.channels());

        // 3) EMAIL 諛쒖넚
        if (channels.contains(DeliveryChannel.EMAIL)) {
            // email ?놁쑝硫?諛쒖넚 遺덇? ???ㅽ뙣濡?泥섎━?좎?, ?ㅽ궢?좎? ?뺤콉 ?좏깮
            if (profile.email() == null || profile.email().isBlank()) {
                throw new IllegalStateException("Recipient email missing");
            }
            emailSender.send(profile.email(), payload.title(), payload.body());
        }

        // 4) PUSH 諛쒖넚
        if (channels.contains(DeliveryChannel.PUSH)) {
            // FCM ?좏겙 ?놁쑝硫?push 遺덇?
            if (profile.fcmToken() == null || profile.fcmToken().isBlank()) {
                throw new IllegalStateException("Recipient fcmToken missing");
            }
            pushSender.send(profile.fcmToken(), payload.title(), payload.body(), payload.deepLink());
        }

        // 5) ?깃났 泥섎━
        // (?꾩슂?섎㈃ delivery 濡쒓렇 ?뚯씠釉붿쓣 ?곕줈 留뚮뱾 ???덉쓬)
    }

    private Set<DeliveryChannel> parseChannels(List<String> raw) {
        // ?붿껌 梨꾨꼸 誘몄?????諛쒖넚 ?앸왂 (Default: None)
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<DeliveryChannel> channels = new HashSet<>();
        for (String ch : raw) {
            if (ch == null) {
                continue;
            }
            try {
                channels.add(DeliveryChannel.valueOf(ch.trim()));
            } catch (Exception e) {
                // ?????녿뒗 梨꾨꼸? 臾댁떆?섍린
            }
        }

        // IN_APP? ?꾩넚 梨꾨꼸???꾨땲誘濡??쒓굅
        channels.remove(DeliveryChannel.IN_APP);
        return channels;
    }

}
