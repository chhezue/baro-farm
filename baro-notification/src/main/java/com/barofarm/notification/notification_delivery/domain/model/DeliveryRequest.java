package com.barofarm.notification.notification_delivery.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * "?꾩넚 ?붿껌" ?꾨찓??紐⑤뜽
 *
 * ?대깽??payload(JSON) -> DeliveryRequest濡?蹂?섑빐??泥섎━?섎㈃ 醫뗭? ?댁쑀:
 * 1) ?몃? ?대깽???ㅽ궎留?蹂寃쎄낵 ?대? 泥섎━ 紐⑤뜽??遺꾨━?????덉쓬
 * 2) ?꾩넚 ?깃났/?ㅽ뙣 寃곌낵(DeliveryResult)瑜?留뚮뱾湲??ъ?
 * 3) idempotency key瑜??ш린???쒖??뷀븷 ???덉쓬
 */
public record DeliveryRequest(
    String eventId,              // 以묐났 泥섎━ 諛⑹?瑜??꾪븳 ?듭떖 ??媛?ν븯硫?UUID)
    String recipientUserId,       // ?꾧뎄?먭쾶 蹂대궡?붿?
    Set<DeliveryChannel> channels,// EMAIL/PUSH/IN_APP 以?臾댁뾿??蹂대궪吏
    String title,
    String body,
    String deepLink,              // push ?대┃ ?대룞??
    Instant occurredAt            // ?대깽??諛쒖깮 ?쒓컖
) {}
