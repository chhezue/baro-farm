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
 * Elasticsearch `user_behavior_logs` 인덱스에 매핑되는 사용자 행동 로그입니다.
 * 장바구니에 담고·빼고·수량을 바꾼 일과 주문을 확정하거나 취소한 일을 한 곳에 모읍니다.
 *
 * <p><b>2026-09-09에 인덱스 둘을 하나로 합쳤습니다.</b> 그전에는 `cart_event_logs`와 `order_event_logs`가
 * 따로 있었는데, 두 문서의 필드가 완전히 같았고 읽는 쪽도 조회 직후 둘을 합쳐서 썼습니다. 나눠서 얻는 것이
 * 없는데 나누는 대가는 있었습니다 — <b>유형별로 최근 5건씩</b> 읽어야 해서 최근순이 유형 경계에서 깨졌습니다.
 * 오늘 장바구니에 20개를 담고 주문은 25일 전 1건뿐인 사용자에게, 25일 전 주문이 오늘 담은 15개보다 우선했습니다.
 * 하나로 합치면 `occurredAt` 내림차순 한 번으로 진짜 최근 것을 가져옵니다.
 *
 * <p>행동의 종류는 {@code eventType}이 갖고 있고, 장바구니인지 주문인지도 거기서 나옵니다
 * ({@code HistoryEventType.getDomain()}) — 따로 도메인 필드를 두지 않는 이유입니다.
 */
@Getter
@Document(indexName = "user_behavior_logs")
@NoArgsConstructor
public class UserBehaviorLogDocument {

    /**
     * 문서 식별자. {@code {도메인}-{파티션}-{오프셋}} 형태이고 주문은 뒤에 항목 순번이 더 붙습니다.
     *
     * <p>도메인을 앞에 붙이는 것이 <b>합치면서 꼭 필요해진 부분</b>입니다. 파티션과 오프셋은 토픽마다
     * 따로 세는 값이라, 장바구니 토픽의 `0-5`와 주문 토픽의 `0-5`가 같은 번호일 수 있습니다. 인덱스가
     * 나뉘어 있을 때는 문제가 없었지만 한 인덱스에 넣으면 한쪽이 다른 쪽을 덮어씁니다.
     *
     * <p>같은 메시지를 다시 처리해도 같은 ID가 나오므로 재시도는 덮어쓰기가 됩니다(중복 적재 방지).
     */
    @Id
    private String id;

    /** 행동을 한 사용자 */
    @Field(type = FieldType.Keyword)
    private UUID userId;

    /** 행동 대상 상품 */
    @Field(type = FieldType.Keyword)
    private UUID productId;

    /** 상품명 (한국어 Nori 형태소 분석기 적용) — 로그를 사람이 읽을 때 쓴다 */
    @Field(type = FieldType.Text, analyzer = "nori")
    private String productName;

    /**
     * 행동 종류. 취향 벡터의 비중이 이 값 하나로 정해진다 —
     * 주문 확정 3 · 담기 2 · 수량 변경 1 · 빼기·취소 0.
     */
    @Field(type = FieldType.Keyword)
    private HistoryEventType eventType;

    /** 행동이 일어난 시각. 30일 조회 창과 최신순 정렬의 기준이다 */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant occurredAt;

    @Builder
    public UserBehaviorLogDocument(String id,
                                   UUID userId,
                                   UUID productId,
                                   String productName,
                                   HistoryEventType eventType,
                                   Instant occurredAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.productName = productName;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }
}
