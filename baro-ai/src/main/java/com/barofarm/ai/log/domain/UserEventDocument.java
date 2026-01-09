package com.barofarm.ai.log.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Document(indexName = "user_event_logs")
@NoArgsConstructor
public class UserEventDocument {

    @Id
    private String id; // Elasticsearch에서는 String ID 사용

    // 통합 이벤트 로그 엔티티 - 미래 확장을 위한 구조
    @Field(type = FieldType.Keyword)
    private UUID userId;

    @Field(type = FieldType.Keyword)
    private String eventType;

    @Field(type = FieldType.Text)
    private String eventData;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant occurredAt;

    @Builder
    public UserEventDocument(UUID userId, String eventType, String eventData, Instant occurredAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.occurredAt = occurredAt;
    }
}
