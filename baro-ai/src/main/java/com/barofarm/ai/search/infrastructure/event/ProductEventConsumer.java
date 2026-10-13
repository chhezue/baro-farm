package com.barofarm.ai.search.infrastructure.event;

import com.barofarm.ai.search.application.ProductIndexService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

/**
 * 상품 서비스(baro-shopping)에서 발행되는 Kafka 상품 이벤트를 수신하여
 * Elasticsearch 상품 색인 및 자동완성 인덱스에 반영하는 이벤트 컨슈머입니다.
 *
 * [주요 처리 흐름]
 * 1. Kafka `product-events` 토픽에서 이벤트를 **묶음**(poll당 최대 N건 — KafkaConsumerConfig)으로 받는다.
 * 2. PRODUCT_CREATED / PRODUCT_UPDATED가 연속되면 모아서 한 번에 색인(indexProducts)한다 — 임베딩 API 1회, ES 저장 인덱스당 1회.
 * 3. PRODUCT_DELETED를 만나면 그때까지 모인 색인을 먼저 내보낸 뒤 삭제한다 — 같은 상품의 "생성 → 삭제" 순서가 뒤집히지 않게.
 * 4. 실패 시: {@link BatchListenerFailedException}에 **실패한 레코드의 위치**를 담아 던진다. 그러면 DefaultErrorHandler는
 *    그 앞의 레코드는 처리된 것으로 커밋하고, 실패 레코드부터 다시 넘긴다(1초 간격 2회, 그래도 실패하면 그 레코드만 건너뛰고 계속).
 *    위치 없이 일반 예외를 던지면 묶음 전체를 처음부터 재시도하고, 재시도가 끝나면 묶음의 **모든** 레코드를 버린다 —
 *    실패 지점 뒤에 있던 멀쩡한 이벤트까지 사라진다(2026-09-06 spring-reviewer 지적, spring-kafka 3.3 소스로 확인).
 *    색인은 상품 ID로 덮어쓰는 연산이라 같은 레코드를 두 번 처리해도 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    /** Elasticsearch 상품 색인 및 삭제를 수행하는 서비스 */
    private final ProductIndexService productIndexService;

    /**
     * product-events 토픽에서 상품 변경 이벤트 묶음을 수신하여 처리하는 리스너 메서드.
     *
     * @param events 한 번의 poll로 받은 상품 이벤트 목록 (1건 이상)
     */
    @KafkaListener(
        topics = "product-events",
        containerFactory = "productEventListenerContainerFactory"
    )
    public void onMessages(List<ProductEvent> events) {
        List<ProductIndexRequest> pending = new ArrayList<>();
        // 모아 둔 색인 요청 중 첫 번째가 묶음의 몇 번째 레코드인지 — flush가 실패하면 여기서부터 다시 받는다
        int pendingStartIndex = -1;

        for (int i = 0; i < events.size(); i++) {
            ProductEvent event = events.get(i);
            // [1단계: 이벤트 본문(data) 유효성 검증]
            // 역직렬화 실패는 KafkaConsumerConfig의 ErrorHandlingDeserializer가 사전 차단하므로 여기까지 오지 않는다.
            // 값이 비어 있는 레코드(tombstone)는 event 자체가 null이라 한 줄로 걸러 둔다.
            if (event == null || event.getData() == null) {
                log.warn("⚠️ [CONSUMER] 본문이 없는 상품 이벤트를 건너뜁니다 - 종류: {}",
                    event == null ? "(레코드 값 없음)" : event.getType());
                continue;
            }

            ProductEvent.ProductEventData data = event.getData();
            log.debug("📨 [CONSUMER] 상품 이벤트 수신 - 종류: {}, 상품: {} ({})",
                event.getType(), data.getProductName(), data.getProductId());

            try {
                // [2단계: 이벤트 타입별 분기 — 색인은 모으고, 삭제는 모인 것을 먼저 내보낸 뒤 처리]
                switch (event.getType()) {
                    case PRODUCT_CREATED, PRODUCT_UPDATED -> {
                        if (pending.isEmpty()) {
                            pendingStartIndex = i;
                        }
                        pending.add(toRequest(data));
                    }
                    case PRODUCT_DELETED -> {
                        flushOrFail(pending, pendingStartIndex);
                        pendingStartIndex = -1;
                        productIndexService.deleteProduct(data.getProductId());
                        log.info("✅ [CONSUMER] 상품 색인 삭제 완료 - 상품: {} ({})",
                            data.getProductName(), data.getProductId());
                    }
                    default -> log.warn("⚠️ [CONSUMER] 알 수 없는 이벤트 종류 - 종류: {}, 상품 ID: {}",
                        event.getType(), data.getProductId());
                }
            } catch (BatchListenerFailedException e) {
                throw e; // flush 실패 — 위치는 이미 담겨 있다
            } catch (Exception e) {
                // [3단계: 삭제 실패 — 이 레코드 위치를 담아 던진다]
                // 예외를 삼키면 재시도가 동작하지 않고, 위치 없이 던지면 묶음 전체가 재시도·폐기된다.
                log.error("❌ [CONSUMER] Failed to process product event - Type: {}, Product: {} ({}), Error: {}",
                    event.getType(), data.getProductName(), data.getProductId(), e.getMessage(), e);
                throw new BatchListenerFailedException("상품 이벤트 처리 실패: " + data.getProductId(), e, i);
            }
        }

        flushOrFail(pending, pendingStartIndex);
    }

    /**
     * 모아 둔 색인 요청을 내보내고, 실패하면 그 묶음의 첫 레코드 위치를 담은 예외로 바꿔 던진다 —
     * 앞서 성공한 레코드는 커밋되고 실패한 묶음부터 다시 받는다.
     */
    private void flushOrFail(List<ProductIndexRequest> pending, int pendingStartIndex) {
        try {
            flush(pending);
        } catch (Exception e) {
            log.error("❌ [CONSUMER] Failed to index product batch ({} products, from record {}) - Error: {}",
                pending.size(), pendingStartIndex, e.getMessage(), e);
            throw new BatchListenerFailedException("상품 묶음 색인 실패", e, pendingStartIndex);
        }
    }

    /**
     * 모아 둔 색인 요청을 한 번에 내보내고 비운다.
     */
    private void flush(List<ProductIndexRequest> pending) {
        if (pending.isEmpty()) {
            return;
        }
        productIndexService.indexProducts(List.copyOf(pending));
        log.info("✅ [CONSUMER] 상품 색인 완료 - {}건", pending.size());
        pending.clear();
    }

    /**
     * 카프카 이벤트 페이로드(ProductEventData)를 서비스 색인 요청 DTO(ProductIndexRequest)로 변환합니다.
     */
    private ProductIndexRequest toRequest(ProductEvent.ProductEventData data) {
        return new ProductIndexRequest(
            data.getProductId(),
            data.getProductName(),
            data.getProductCategoryId(),
            data.getProductCategoryCode(),
            data.getProductCategoryName(),
            data.getPrice(),
            data.getStatus(),
            data.getUpdatedAt());
    }
}
