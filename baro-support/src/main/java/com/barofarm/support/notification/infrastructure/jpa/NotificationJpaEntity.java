package com.barofarm.support.notification.infrastructure.jpa;

import com.barofarm.support.notification.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_notification_user_created", columnList = "user_id, created_at")
})
@Getter
@Setter
public class NotificationJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", length = 20, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private NotificationType type;

    @Column(length = 80, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "related_url", length = 200)
    private String relatedUrl;

    @Column(name = "read_yn", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    protected NotificationJpaEntity() {}

}
