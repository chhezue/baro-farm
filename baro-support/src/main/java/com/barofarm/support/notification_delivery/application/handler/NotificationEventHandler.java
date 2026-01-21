package com.barofarm.support.notification_delivery.application.handler;

import com.barofarm.support.notification_delivery.application.service.NotificationDeliveryService;
import com.barofarm.support.notification_delivery.domain.event.NotificationEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "이벤트 수신"과 "도메인 헨들러"를 분리하기 위한 핸들러
 *
 * - consumer는 메시지를 받고 파싱하는 책임만 가진다.
 * - 실제 비즈니스 로직은 DeliveryService로 위임한다.
 * */

@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationDeliveryService deliveryService;

    public void handle(NotificationEventPayload payload) {
        // 예 : type 필터링 기능
        // if (!"NOTIFICATION_CREATED".equals(payLoad.type())) return;

        deliveryService.deliver(payload);
    }
}
