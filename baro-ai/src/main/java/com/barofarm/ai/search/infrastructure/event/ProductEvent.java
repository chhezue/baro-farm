package com.barofarm.ai.search.infrastructure.event;

import java.time.Instant;
import java.util.UUID;

/**
 * `product-events` 토픽으로 들어오는 상품 변경 이벤트 중 이 모듈이 읽는 필드만 담습니다.
 *
 * <p>발행하는 쪽(`baro-shopping`)의 클래스를 직접 쓰지 않고 같은 모양을 여기 따로 두는 것은 이 저장소의
 * 방식입니다 — 모듈끼리 패키지를 공유하지 않기 위해서입니다. 상류가 필드를 더 보내도 Kafka 역직렬화기가
 * 모르는 필드를 무시하므로 수신에 지장이 없습니다.
 *
 * <p>2026-09-09에 `record`로 바꿨습니다. 그전에는 이 파일만 Lombok `@Getter`/`@Setter` 클래스여서
 * 다른 이벤트 모델 네 개(`CartLogEvent`·`OrderLogEvent`·`ReviewEvent`·`ReviewSummaryEvent`)와 모양이
 * 달랐습니다. 역직렬화에 setter가 필요하다는 오해가 있었는데, 나머지 넷이 이미 record로 정상 소비되고
 * 있어 근거가 없었습니다.
 *
 * @param type 이벤트 종류 (생성·수정·삭제)
 * @param data 어떤 상품에 대한 사건인지. 값이 비어 온 레코드(tombstone)에서는 {@code null}일 수 있다
 */
public record ProductEvent(
    ProductEventType type,
    ProductEventData data) {

    public enum ProductEventType {
        PRODUCT_CREATED,
        PRODUCT_UPDATED,
        PRODUCT_DELETED
    }

    /**
     * 색인에 필요한 상품 속성입니다.
     *
     * @param updatedAt 상류가 이벤트를 내보낸 시각. 상품이 바뀐 시각이 아니라 발행 시각이다
     */
    public record ProductEventData(
        UUID productId,
        String productName,
        UUID productCategoryId,
        String productCategoryCode,
        String productCategoryName,
        Long price,
        String status,
        Instant updatedAt) { }
}
