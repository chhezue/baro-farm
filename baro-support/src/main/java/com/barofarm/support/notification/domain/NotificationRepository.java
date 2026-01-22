package com.barofarm.support.notification.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

// 도메인 포트 : 추상화 부분 (Interface)
// 구현은 infrastructure/jpa 안에
public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findRecentByUserId(String userId, int limit);

    long countUnreadByUserId(String userId, OffsetDateTime now);

    Optional<Notification> findById(String notificationId);

    int markAllUnreadAsRead(String userId, OffsetDateTime now);

}
