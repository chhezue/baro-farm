package com.barofarm.ai.log.domain;

import com.barofarm.log.history.model.HistoryEventType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch 'order_event_logs' 인덱스에 매핑되는 주문 행동 로그 엔티티.
 *
 * 사용자의 주문 확정 및 취소 이력을 주문 상품 단위로 분할하여 보관하며,
 * 사용자 취향 프로필 벡터 산출 시 가장 높은 중요도(구매 확정 3점)를 적용하기 위한 원천 데이터로 사용됩니다.
 */
@Getter
@Document(indexName = "order_event_logs")
@NoArgsConstructor
public class OrderLogDocument {

    /** 문서 식별자 (Kafka '{partition}-{offset}-{itemIndex}' 기반 생성으로 복수 상품 라인 보존 및 멱등성 보장) */
    @Id
    private String id;

    /** 주문을 수행한 사용자 ID */
    @Field(type = FieldType.Keyword)
    private UUID userId;

    /** 주문된 상품 ID */
    @Field(type = FieldType.Keyword)
    private UUID productId;

    /** 상품명 (한국어 Nori 형태소 분석기 적용) */
    @Field(type = FieldType.Text, analyzer = "nori")
    private String productName;

    /** 상품 카테고리 코드 */
    @Field(type = FieldType.Keyword)
    private String categoryCode;

    /**
     * 주문 이벤트 유형 (ORDER_CONFIRMED, ORDER_CANCELLED).
     * 취향 벡터 계산 시 이벤트 중요도 매핑(확정 3점, 취소 0점)에 사용됩니다.
     */
    @Field(type = FieldType.Keyword)
    private HistoryEventType eventType;

    /** 구매 수량 (취향 벡터 가중치 산출 시 관심 강도로 반영, 최대 5로 제한됨) */
    @Field(type = FieldType.Integer)
    private Integer quantity;

    /** 주문 발생 일시 (취향 벡터 계산의 30일 조회 창·최신순 정렬 기준) */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant occurredAt;

    @Builder
    public OrderLogDocument(String id,
                            UUID userId,
                            UUID productId,
                            String productName,
                            String categoryCode,
                            HistoryEventType eventType,
                            Integer quantity,
                            Instant occurredAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.productName = productName;
        this.categoryCode = categoryCode;
        this.eventType = eventType;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
    }
}
