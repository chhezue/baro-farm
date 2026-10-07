package com.barofarm.ai.search.infrastructure.event;

import com.barofarm.ai.search.application.ProductIndexService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 상품 서비스(baro-product)에서 발행되는 Kafka 상품 이벤트를 수신하여
 * Elasticsearch 상품 색인 및 자동완성 인덱스에 실시간으로 반영하는 이벤트 컨슈머입니다.
 *
 * [주요 처리 흐름]
 * 1. Kafka `product-events` 토픽으로부터 상품 생성/수정/삭제 이벤트를 수신합니다.
 * 2. PRODUCT_CREATED / PRODUCT_UPDATED: 임베딩 생성 및 재료명 정규화를 거쳐 ES에 문서를 색인(indexProduct)합니다.
 * 3. PRODUCT_DELETED: 메인 상품 인덱스 및 자동완성 인덱스에서 해당 상품을 삭제(deleteProduct)합니다.
 * 4. 실패 시: DefaultErrorHandler와 FixedBackOff(1초 2회)가 재시도를 수행할 수 있도록 잡은 예외를 그대로 rethrow합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    /** Elasticsearch 상품 색인 및 삭제를 수행하는 서비스 */
    private final ProductIndexService productIndexService;

    /**
     * product-events 토픽에서 상품 변경 이벤트를 수신하여 처리하는 리스너 메서드.
     *
     * @param event 상품 이벤트 메시지 객체
     */
    @KafkaListener(
        topics = "product-events",
        containerFactory = "productEventListenerContainerFactory"
    )
    public void onMessage(ProductEvent event) {
        // [1단계: 이벤트 본문(data) 유효성 검증]
        // 역직렬화 실패는 KafkaConsumerConfig의 ErrorHandlingDeserializer가 사전 차단하므로 여기까지 오지 않는다.
        // 다만 값이 비어 있는 레코드(tombstone)가 들어오면 event 자체가 null이라 바로 NPE가 난다.
        // 지금 이 토픽에 tombstone을 쓰는 발행자는 없지만, 그때 원인을 알 수 있게 한 줄로 걸러 둔다.
        if (event == null || event.getData() == null) {
            log.warn("⚠️ [CONSUMER] 본문이 없는 상품 이벤트를 건너뜁니다 - 종류: {}",
                event == null ? "(레코드 값 없음)" : event.getType());
            return;
        }

        ProductEvent.ProductEventData data = event.getData();
        log.debug("📨 [CONSUMER] 상품 이벤트 수신 - 종류: {}, 상품: {} ({})",
            event.getType(), data.getProductName(), data.getProductId());

        try {
            // [2단계: 이벤트 타입별 분기 처리 (생성/수정 색인 vs 삭제)]
            switch (event.getType()) {
                case PRODUCT_CREATED, PRODUCT_UPDATED -> {
                    // 상품 생성 및 수정: 텍스트 임베딩 생성 및 요리 재료 표준명 정규화 후 ES 색인
                    productIndexService.indexProduct(toRequest(data));
                    log.info("✅ [CONSUMER] 상품 색인 완료 - 종류: {}, 상품: {} ({})",
                        event.getType(), data.getProductName(), data.getProductId());
                }
                case PRODUCT_DELETED -> {
                    // 상품 삭제: 메인 검색 인덱스 및 자동완성 인덱스에서 문서 영구 제거
                    productIndexService.deleteProduct(data.getProductId());
                    log.info("✅ [CONSUMER] 상품 색인 삭제 완료 - 상품: {} ({})",
                        data.getProductName(), data.getProductId());
                }
                default -> log.warn("⚠️ [CONSUMER] 알 수 없는 이벤트 종류 - 종류: {}, 상품 ID: {}",
                    event.getType(), data.getProductId());
            }
        } catch (Exception e) {
            // [3단계: 예외 로깅 및 Kafka 리스너 컨테이너 재시도 유도]
            // 예외를 여기서 삼켜버리면 Kafka의 재시도(FixedBackOff) 메커니즘이 동작하지 않으므로,
            // 에러 로그를 상세히 남긴 후 예외를 상위로 다시 던져 컨테이너 레벨에서 재시도하도록 합니다.
            log.error("❌ [CONSUMER] Failed to process product event - Type: {}, Product ID: {}, Name: {}, Error: {}",
                event.getType(), data.getProductId(), data.getProductName(), e.getMessage(), e);
            throw e;
        }
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
