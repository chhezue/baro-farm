package com.barofarm.ai.event.consumer;

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
 * 주문에 포함된 상품 항목별로 Elasticsearch에 행동 로그를 적재합니다. 여기서 하는 일은 로그 적재
 * 하나뿐입니다 — 2026-09-08까지는 취향 벡터 재계산까지 비동기로 요청했으나, 그 계산을 추천 요청
 * 시점으로 옮기면서 이 경로에서 뺐습니다(근거: {@code TasteProfile}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    /** Elasticsearch 행동 로그 저장 서비스 */
    private final LogWriteService logWriteService;

    /**
     * 주문 이벤트를 수신하여 상품 항목별로 행동 로그를 저장하는 리스너 메서드.
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
            // 이벤트 타입은 받은 그대로 저장하고, 비중은 취향 벡터를 계산할 때 매깁니다(ORDER_CONFIRMED는 3).
            // ORDER_CANCELLED는 현재 발행하는 모듈이 없고, 들어와도 주문 ID를 저장하지 않아 어느 확정을
            // 취소하는지 짝지을 수 없습니다 — docs/audits/taste-vector/006-behavior-weight-semantics.md B-2
            List<OrderLogEvent.OrderEventData.OrderItemData> orderItems =
                data.orderItems() != null ? data.orderItems() : List.of();

            for (int index = 0; index < orderItems.size(); index++) {
                OrderLogEvent.OrderEventData.OrderItemData item = orderItems.get(index);
                logWriteService.saveBehaviorLog(
                    EventDomain.ORDER,
                    partition + "-" + offset + "-" + index,
                    event.userId(),
                    item.productId(),
                    item.productName(),
                    event.event(),
                    event.ts().toInstant()
                );
            }

            log.info(
                "[ORDER_CONSUMER] Successfully saved order event logs - Type: {}, User: {}, Items: {}",
                event.event(), event.userId(), orderItems.size());
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
