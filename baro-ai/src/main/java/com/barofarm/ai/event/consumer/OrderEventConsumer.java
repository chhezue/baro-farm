package com.barofarm.ai.event.consumer;

import com.barofarm.ai.embedding.application.UserProfileRefreshTrigger;
import com.barofarm.ai.event.model.OrderLogEvent;
import com.barofarm.ai.log.application.LogWriteService;
import com.barofarm.log.history.model.EventDomain;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


/**
 * 주문 이벤트 Kafka Consumer.
 *
 * 'order-events' 토픽에서 주문 이벤트(주문 확정, 주문 취소 등)를 수신하여
 * 주문에 포함된 상품 항목별로 Elasticsearch에 행동 로그를 적재하고,
 * 개인화 추천을 위한 사용자 취향 벡터 재계산을 비동기로 요청합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    /** Elasticsearch 행동 로그 저장 서비스 */
    private final LogWriteService logWriteService;

    /** 사용자 프로필 벡터 비동기 갱신 트리거 컴포넌트 */
    private final UserProfileRefreshTrigger userProfileRefreshTrigger;

    /**
     * 주문 이벤트를 수신하여 항목별 로그 저장 및 프로필 갱신을 수행하는 리스너 메서드.
     *
     * @param event 수신된 주문 이벤트 페이로드
     * @param partition 수신된 Kafka 파티션 번호
     * @param offset 수신된 Kafka 오프셋
     */
    // 그룹 ID는 여기 적지 않는다. orderEventConsumerFactory가 spring.kafka.consumer.group-id에 '-order'를 붙여
    // 만들고, 어노테이션에 값을 쓰면 그 값이 팩토리 설정을 덮어써 두 곳을 같이 고쳐야 하기 때문이다.
    @KafkaListener(
        topics = "order-events",
        containerFactory = "orderEventListenerContainerFactory"
    )
    public void onMessage(OrderLogEvent event,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        OrderLogEvent.OrderEventData data = event.payload();

        log.debug(
            "[ORDER_CONSUMER] Received order event - Type: {}, User ID: {}, Order ID: {}",
            event.event(), event.userId(), data.orderId());

        // [1단계: 이벤트 도메인 및 유효성 검증]
        // 이벤트 종류(event.event())가 누락되었거나 도메인이 ORDER가 아닌 타 도메인 이벤트가 수신된 경우,
        // 잘못된 데이터가 저장되거나 이후 취향 계산 오류로 번지는 것을 방지하기 위해 경고 로그를 남기고 처리를 스킵합니다.
        if (event.event() == null || event.event().getDomain() != EventDomain.ORDER) {
            log.warn(
                "[ORDER_CONSUMER] Skipping event with unexpected type - Type: {}, User: {}, Order: {}",
                event.event(), event.userId(), data.orderId());
            return;
        }

        try {
            // [2단계: 주문 상품 항목별 순회 및 Elasticsearch 로그 영속화 (멱등성 보장)]
            // 주문 1건에 여러 상품이 포함될 수 있으므로 orderItems 목록을 순회하며 상품 단위로 개별 저장합니다.
            // 문서 ID를 '{partition}-{offset}-{index}' 조합으로 생성하여,
            // 한 주문 내 동일 상품이 중복 라인으로 존재하더라도 데이터 유실 없이 각각 저장되며,
            // Kafka 컨슈머 재시도 시에도 동일 ID를 덮어쓰므로 멱등성이 보장됩니다.
            // (이벤트 타입은 ORDER_CONFIRMED: 관심도 최상 3점, ORDER_CANCELLED: 취소 0점으로 원본 저장)
            List<OrderLogEvent.OrderEventData.OrderItemData> orderItems =
                data.orderItems() != null ? data.orderItems() : List.of();

            for (int index = 0; index < orderItems.size(); index++) {
                OrderLogEvent.OrderEventData.OrderItemData item = orderItems.get(index);
                logWriteService.saveOrderEventLog(
                    partition + "-" + offset + "-" + index,
                    event.userId(),
                    item.productId(),
                    item.productName(),
                    item.categoryCode(),
                    event.event(),
                    item.quantity(),
                    event.ts().toInstant()
                );
            }

            log.info(
                "[ORDER_CONSUMER] Successfully saved order event logs - Type: {}, User: {}, Items: {}",
                event.event(), event.userId(), orderItems.size());

            // [3단계: 사용자 취향 벡터 비동기 갱신 트리거]
            // 주문 발생 행동을 사용자 취향 벡터에 즉시 반영하기 위해,
            // UserProfileRefreshTrigger를 통해 백그라운드 스레드 풀에서 비동기로 프로필 재계산을 실행합니다.
            userProfileRefreshTrigger.refresh(event.userId());
        } catch (Exception e) {
            // [4단계: 예외 로깅 및 Kafka 재시도 유도]
            // 로그 저장 또는 처리 중 예외 발생 시 에러 로그를 기록하고 예외를 다시 던져(rethrow),
            // Kafka DefaultErrorHandler(1초 간격 2회 재시도)가 동작하도록 합니다.
            log.error(
                "[ORDER_CONSUMER] Failed to process order event - Type: {}, User: {}, Order: {}, Error: {}",
                event.event(), event.userId(), data.orderId(), e.getMessage(), e);
            throw e;
        }
    }
}
