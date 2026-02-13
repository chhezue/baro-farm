package com.barofarm.notification.notification.infrastructure.jpa;

import com.barofarm.notification.notification.domain.Notification;
import com.barofarm.notification.notification.domain.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

   @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity e = mapToEntity(notification);
        NotificationJpaEntity saved = jpa.save(e);
        return mapToDomain(saved);
    }

    @Override
    public List<Notification> findRecentByUserId(String userId, int limit) {
        return jpa.findTop50ByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::mapToDomain)
            .toList();
    }

    @Override
    public long countUnreadByUserId(String userId, OffsetDateTime now) {
        return jpa.countUnread(userId, now);
    }

    @Override
    public Optional<Notification> findById(String notificationId) {
        return jpa.findById(notificationId).map(this::mapToDomain);
    }

    @Override
    public int markAllUnreadAsRead(String userId, OffsetDateTime now) {
        return jpa.markAllUnreadAsRead(userId, now);
    }

    // mapping helppers
    private NotificationJpaEntity mapToEntity(Notification n) {
        NotificationJpaEntity e = new NotificationJpaEntity();
        e.setId(n.getId());
        e.setUserId(n.getUserId());
        e.setType(n.getType());
        e.setTitle(n.getTitle());
        e.setContent(n.getContent());
        e.setRead(n.isRead());
        e.setCreatedAt(n.getCreatedAt());
        e.setExpiredAt(n.getExpiresAt());
        return e;
    }

    private Notification mapToDomain(NotificationJpaEntity e) {
        // Domain ?앹꽦 洹쒖튃???ㅼ떆 ?쒖슦湲곕낫?? DB 濡쒕뵫? "蹂듭썝" ?깃꺽?대?濡?蹂꾨룄 ?앹꽦 濡쒖쭅??媛??
        return Notification.create(
            e.getId(),
            e.getUserId(),
            e.getType(),
            e.getTitle(),
            e.getContent(),
            e.getRelatedUrl(),
            e.getCreatedAt()
        );
    }


}
