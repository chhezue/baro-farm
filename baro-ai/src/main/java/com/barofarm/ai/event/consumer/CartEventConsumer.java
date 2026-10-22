package com.barofarm.ai.event.consumer;

import com.barofarm.ai.event.model.CartLogEvent;
import com.barofarm.ai.log.application.LogWriteService;
import com.barofarm.log.history.model.EventDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 장바구니 이벤트 Kafka Consumer.
 *
 * 'cart-events' 토픽에서 장바구니 변경 이벤트(추가, 수량변경, 삭제 등)를 수신하여
 * Elasticsearch에 사용자 행동 로그를 적재합니다. 여기서 하는 일은 로그 적재 하나뿐입니다 —
 * 2026-09-08까지는 취향 벡터 재계산까지 비동기로 요청했으나, 그 계산을 추천 요청 시점으로 옮기면서
 * 이 경로에서 뺐습니다(근거: {@code TasteProfile}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartEventConsumer {

    /** Elasticsearch 행동 로그 저장 서비스 */
    private final LogWriteService logWriteService;

    /**
     * 장바구니 이벤트를 수신하여 행동 로그를 저장하는 리스너 메서드.
     *
     * @param event 수신된 장바구니 이벤트 페이로드
     * @param partition 수신된 Kafka 파티션 번호
     * @param offset 수신된 Kafka 오프셋
     */
    // 그룹 ID는 여기 적지 않는다. cartEventConsumerFactory가 spring.kafka.consumer.group-id에 '-cart'를 붙여
    // 만들고, 어노테이션에 값을 쓰면 그 값이 팩토리 설정을 덮어써 두 곳을 같이 고쳐야 하기 때문이다.
    @KafkaListener(
        topics = "cart-events",
        containerFactory = "cartEventListenerContainerFactory"
    )
    public void onMessage(CartLogEvent event,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        CartLogEvent.CartEventData data = event.payload();

        log.debug("[CART_CONSUMER] Received cart event - Type: {}, User ID: {}, Product: {}",
            event.event(), event.userId(), data.productName());

        // [1단계: 이벤트 도메인 및 유효성 검증]
        // 이벤트 종류(event.event())가 누락되었거나 도메인이 CART가 아닌 타 도메인 이벤트가 수신된 경우,
        // 불일치 데이터 처리를 방지하기 위해 경고 로그를 남기고 즉시 종료합니다.
        if (event.event() == null || event.event().getDomain() != EventDomain.CART) {
            log.warn("[CART_CONSUMER] Skipping event with unexpected type - Type: {}, User: {}, Product: {}",
                event.event(), event.userId(), data.productName());
            return;
        }

        try {
            // [2단계: Elasticsearch 행동 로그 영속화 (멱등성 보장)]
            // Kafka의 '{partition}-{offset}' 조합을 Elasticsearch 문서 ID로 사용하여,
            // 컨슈머 재시도가 발생하더라도 동일 문서를 덮어쓰도록 하여 로그 중복 적재를 방지합니다.
            // 취향 계산에 실제로 쓰이는 것만 저장합니다 — 행동 종류(추가 2점, 수량변경 1점, 삭제 0점)와
            // 발생 일시(30일 조회 창·최신순 정렬의 기준)입니다.
            logWriteService.saveBehaviorLog(
                EventDomain.CART,
                partition + "-" + offset,
                event.userId(),
                data.productId(),
                data.productName(),
                event.event(),
                event.ts().toInstant()
            );
            log.info("[CART_CONSUMER] Successfully saved cart event - Type: {}, User: {}, Product: {}",
                event.event(), event.userId(), data.productName());
        } catch (Exception e) {
            // [4단계: 예외 로깅 및 Kafka 재시도 유도]
            // 처리 중 예외 발생 시 에러 로그를 남기고 예외를 다시 던져(rethrow),
            // 컨테이너 에러 핸들러(DefaultErrorHandler)의 고정 재시도 정책이 동작하도록 합니다.
            log.error("[CART_CONSUMER] Failed to process cart event - Type: {}, User: {}, Product: {}, Error: {}",
                event.event(), event.userId(), data.productName(), e.getMessage(), e);
            throw e;
        }
    }
}
