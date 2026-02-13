package com.barofarm.notification.notification.infrastructure.id;

import com.barofarm.notification.notification.domain.NotificationIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Domain Port(NotificationIdGenerator)??援ы쁽泥?
 * 媛???⑥닚??UUID 湲곕컲 ID 諛쒓툒
 *
 * ?ㅻТ?먯꽌??ULID(?쒓컙 ?뺣젹)
 * */
@Component
public class UuidNotificationIdGenerator implements NotificationIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
