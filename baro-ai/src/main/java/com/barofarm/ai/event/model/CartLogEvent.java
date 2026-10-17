package com.barofarm.ai.event.model;

import com.barofarm.log.history.model.HistoryEventType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * `cart-events` 토픽으로 들어오는 장바구니 변경 이벤트 중 이 모듈이 실제로 읽는 필드만 담는 레코드입니다.
 *
 * @param event   이벤트 종류 — 담기·수량변경·빼기에 따라 취향 가중치가 달라진다
 * @param ts      이벤트 발생 시각 — 최근 30일만 보는 조회 창과 최신순 정렬의 기준값이다.
 *                시간이 비중을 바꾸지는 않는다(시간 감쇠 축은 2026-09-06에 뺐다)
 * @param userId  행동한 사용자
 * @param payload 어떤 상품에 대한 행동인지
 */
public record CartLogEvent(
    HistoryEventType event,
    OffsetDateTime ts,
    UUID userId,
    CartEventData payload) {

    /**
     * 장바구니 이벤트의 상품 정보입니다.
     *
     * <p>보내는 쪽의 전체 모양은 `baro-common`의 `com.barofarm.log.history.model.CartEventData`에 있고,
     * 값을 채우는 곳은 `baro-shopping`의 `cart.history.Cart*HistoryPayloadMapper`입니다. 거기엔
     * `cartId`·`cartItemId`도 있지만 취향 벡터는 "누가 어떤 상품에 무엇을 했나"만 쓰고 장바구니 식별자는
     * 보지 않으므로 옮겨 적지 않았습니다 — Kafka 역직렬화기가 모르는 필드를 무시하도록 돼 있어
     * (spring-kafka `JsonDeserializer` 기본 매퍼) 수신에 지장이 없습니다.
     *
     * <p>`baro-common`의 클래스를 직접 쓰지 않고 여기 따로 두는 것은 이 저장소의 기존 방식을 따른 것입니다
     * (`search.infrastructure.event.ProductEvent` 주석 참고 — 모듈 간 직접 의존을 피하려고 같은 모양을
     * 각 모듈에 둔다). 다만 그 클래스는 상류 필드를 전부 옮겨 적고 있어 이 파일과 방침이 어긋난다 —
     * 어느 쪽으로 통일할지는 아직 정해지지 않았습니다.
     *
     * <p>상류는 카테고리 코드와 수량도 함께 보내지만 여기서는 받지 않습니다. 둘 다 행동 로그에 저장만 되고
     * 읽는 곳이 없어 2026-09-07에 뺐습니다 — 수량은 선호의 세기가 아니라 소비량이라 가중치에서 빠졌고
     * (취향 계산은 행동 종류 하나로만 정해집니다), 카테고리 선호를 쓰는 계획도 없습니다.
     * 근거: docs/audits/cross-cutting/003-stale-comments-and-write-only-fields.md
     *
     * @param productId    행동 대상 상품
     * @param productName  상품 명칭 — 로그 본문과 추적용
     */
    public record CartEventData(
        UUID productId,
        String productName
    ) { }
}
