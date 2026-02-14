package com.barofarm.notification.notification.infrastructure.id;

import com.barofarm.notification.notification.domain.NotificationIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * NotificationIdGenerator의 UUID 기반 구현체.
 */
@Component
public class UuidNotificationIdGenerator implements NotificationIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}

