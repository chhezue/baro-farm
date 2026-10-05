package com.barofarm.ai.event.consumer;

import com.barofarm.ai.embedding.application.UserProfileRefreshTrigger;
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
 * Elasticsearch에 사용자 행동 로그를 적재하고, 개인화 추천을 위한 사용자 취향 벡터 재계산을 비동기로 요청합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartEventConsumer {

    /** Elasticsearch 행동 로그 저장 서비스 */
    private final LogWriteService logWriteService;

    /** 사용자 프로필 벡터 비동기 갱신 트리거 컴포넌트 */
    private final UserProfileRefreshTrigger userProfileRefreshTrigger;

    /**
     * 장바구니 이벤트를 수신하여 로그 저장 및 프로필 갱신을 수행하는 리스너 메서드.
     *
     * @param event 수신된 장바구니 이벤트 페이로드
     * @param partition 수신된 Kafka 파티션 번호
     * @param offset 수신된 Kafka 오프셋
     */
    @KafkaListener(
        topics = "cart-events",
        groupId = "ai-service-cart",
        containerFactory = "cartEventListenerContainerFactory"
    )
    public void onMessage(CartLogEvent event,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        CartLogEvent.CartEventData data = event.payload();

        log.debug("[CART_CONSUMER] Received cart event - Type: {}, User ID: {}, Product: {}, Quantity: {}",
            event.event(), event.userId(), data.productName(), data.quantity());

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
            // 이벤트 타입(추가 2점, 수량변경 1점, 삭제 0점)과 수량, 발생 일시는 가중치 계산을 위해 원본 그대로 저장합니다.
            logWriteService.saveCartEventLog(
                partition + "-" + offset,
                event.userId(),
                data.productId(),
                data.productName(),
                data.categoryCode(),
                event.event(),
                data.quantity(),
                event.ts().toInstant()
            );
            log.info("[CART_CONSUMER] Successfully saved cart event - Type: {}, User: {}, Product: {}",
                event.event(), event.userId(), data.productName());

            // [3단계: 사용자 취향 벡터 비동기 갱신 트리거]
            // 장바구니 변경 사항이 사용자 취향 벡터에 반영되도록 UserProfileRefreshTrigger에 백그라운드 갱신을 위임합니다.
            userProfileRefreshTrigger.refresh(event.userId());
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
