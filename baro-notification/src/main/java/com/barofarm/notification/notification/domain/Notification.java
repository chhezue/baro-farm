package com.barofarm.notification.notification.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/*
 * [Domain Entity]
 * - Represents notification domain state and rules.
 * - Transport/storage concerns are handled outside this class.
 */
public class Notification {

    private final String id; // Notification PK
    private final String userId; // Recipient user id
    private final NotificationType type; // Notification category
    private final String title; // Display title
    private final String content; // Message body
    private final String relatedUrl; // Optional related URL
    private boolean read; // Read flag
    private final OffsetDateTime createdAt; // Creation time
    private final OffsetDateTime expiresAt; // Expiration time

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Notification(
        String id,
        String userId,
        NotificationType type,
        String title,
        String content,
        String relatedUrl,
        boolean read,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.relatedUrl = relatedUrl;
        this.read = read;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // TODO: define creation rules
    public static Notification create(
        String id,
        String userId,
        NotificationType type,
        String title,
        String content,
        String relatedUrl,
        OffsetDateTime now
    ) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(type, "type is required");

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        OffsetDateTime expireAt = now.plusDays(30); // default retention

        return new Notification(
            id,
            userId,
            type,
            title,
            content,
            relatedUrl,
            false,
            now,
            expireAt
        );
    }

    public void markAsRead() {
        this.read = true;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getRelatedUrl() {
        return relatedUrl;
    }

    public boolean isRead() {
        return read;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
