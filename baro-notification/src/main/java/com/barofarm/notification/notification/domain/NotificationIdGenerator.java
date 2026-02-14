package com.barofarm.notification.notification.domain;

/**
 * 알림 ID 생성 포트.
 * 도메인이 ID 생성 기술(UUID/ULID)에 직접 의존하지 않도록 분리한다.
 */
public interface NotificationIdGenerator {

    /**
     * @return 생성된 Notification ID 문자열
     */

    String generate();

}
