package com.barofarm.notification.notification.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findRecentByUserId(String userId, int limit);

    long countUnreadByUserId(String userId, OffsetDateTime now);

    Optional<Notification> findById(String notificationId);

    int markAllUnreadAsRead(String userId, OffsetDateTime now);

}
