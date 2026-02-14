package com.barofarm.notification.notification_delivery.application.handler;

import com.barofarm.notification.notification_delivery.application.service.NotificationDeliveryService;
import com.barofarm.notification.notification_delivery.domain.event.NotificationEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이벤트 수신 계층과 전달 비즈니스 로직을 분리하는 핸들러.
 */

@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationDeliveryService deliveryService;

    public void handle(NotificationEventPayload payload) {
        // if (!"NOTIFICATION_CREATED".equals(payLoad.type())) return;

        deliveryService.deliver(payload);
    }
}

