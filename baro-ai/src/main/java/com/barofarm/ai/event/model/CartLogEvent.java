package com.barofarm.ai.event.model;

import com.barofarm.log.history.model.HistoryEventType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * `cart-events` 토픽으로 들어오는 장바구니 변경 이벤트 중 이 모듈이 실제로 읽는 필드만 담는 레코드입니다.
 *
 * @param event   이벤트 종류 — 담기·수량변경·빼기에 따라 취향 가중치가 달라진다
 * @param ts      이벤트 발생 시각 — 최근 행동에 더 큰 가중치를 주는 계산의 기준값
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
     * @param productId    행동 대상 상품
     * @param productName  상품 명칭 — 로그 본문과 추적용
     * @param categoryCode 카테고리 코드 — 행동 로그 문서에 저장만 된다. 취향 벡터 계산은 아직 읽지 않는다
     * @param quantity     수량 — 이벤트 종류별 가중치에 곱해진다
     */
    public record CartEventData(
        UUID productId,
        String productName,
        String categoryCode,
        Integer quantity
    ) { }
}
