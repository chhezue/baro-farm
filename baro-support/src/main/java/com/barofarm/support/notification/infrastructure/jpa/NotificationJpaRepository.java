package com.barofarm.support.notification.infrastructure.jpa;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    List<NotificationJpaEntity> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    @Query("select count(n) from NotificationJpaEntity n " +
        "where n.userId = :userId and n.read = false and n.expiredAt >:now")
    long countUnread(String userId, OffsetDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationJpaEntity n set n.read = true " +
        "where n.userId = :userId and n.read = false and n.expiredAt > :now")
    int markAllUnreadAsRead(String userId, OffsetDateTime now);

}
