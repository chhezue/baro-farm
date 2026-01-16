package com.barofarm.support.notification.infrastructure.id;

import com.barofarm.support.notification.domain.NotificationIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Domain Port(NotificationIdGenerator)의 구현체
 * 가장 단순한 UUID 기반 ID 발급
 *
 * 실무에서는 ULID(시간 정렬)
 * */
@Component
public class UuidNotificationIdGenerator implements NotificationIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
