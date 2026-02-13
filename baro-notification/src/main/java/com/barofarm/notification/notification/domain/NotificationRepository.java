package com.barofarm.notification.notification.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

// ?꾨찓???ы듃 : 異붿긽??遺遺?(Interface)
// 援ы쁽? infrastructure/jpa ?덉뿉
public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findRecentByUserId(String userId, int limit);

    long countUnreadByUserId(String userId, OffsetDateTime now);

    Optional<Notification> findById(String notificationId);

    int markAllUnreadAsRead(String userId, OffsetDateTime now);

}
