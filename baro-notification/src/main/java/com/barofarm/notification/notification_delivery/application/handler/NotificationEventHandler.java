package com.barofarm.notification.notification_delivery.application.handler;

import com.barofarm.notification.notification_delivery.application.service.NotificationDeliveryService;
import com.barofarm.notification.notification_delivery.domain.event.NotificationEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "?대깽???섏떊"怨?"?꾨찓???⑤뱾??瑜?遺꾨━?섍린 ?꾪븳 ?몃뱾??
 *
 * - consumer??硫붿떆吏瑜?諛쏄퀬 ?뚯떛?섎뒗 梨낆엫留?媛吏꾨떎.
 * - ?ㅼ젣 鍮꾩쫰?덉뒪 濡쒖쭅? DeliveryService濡??꾩엫?쒕떎.
 * */

@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationDeliveryService deliveryService;

    public void handle(NotificationEventPayload payload) {
        // ??: type ?꾪꽣留?湲곕뒫
        // if (!"NOTIFICATION_CREATED".equals(payLoad.type())) return;

        deliveryService.deliver(payload);
    }
}
