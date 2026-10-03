package com.barofarm.ai.search.infrastructure.event;

import com.barofarm.ai.search.application.ProductIndexService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ProductIndexService productIndexService;

    // Product 모듈에서 상품 CRUD 시 product-events 토픽에 메세지 발행
    @KafkaListener(
        topics = "product-events",
        groupId = "search-service",
        containerFactory = "productEventListenerContainerFactory"
    )
    public void onMessage(ProductEvent event) {
        // 역직렬화에 실패한 레코드는 KafkaConsumerConfig의 팩토리가 null로 바꿔 넘긴다.
        // 다시 시도해도 같은 결과라 재시도 없이 바로 건너뛴다.
        if (event == null || event.getData() == null) {
            log.warn("⚠️ [CONSUMER] Skipping product event with no payload (deserialization failed)");
            return;
        }
        ProductEvent.ProductEventData data = event.getData();
        log.info("📨 [CONSUMER] Received product event - Type: {}, Product ID: {}, Name: {}, " +
                "Category: {} ({}), Price: {}",
                event.getType(), data.getProductId(), data.getProductName(),
                data.getProductCategoryName(), data.getProductCategoryId(), data.getPrice());

        try {
            switch (event.getType()) {
                case PRODUCT_CREATED -> {
                    log.info(
                        "🆕 [CONSUMER] Processing PRODUCT_CREATED - ID: {}, Name: {}, Category: {} ({}), Price: {}",
                        data.getProductId(), data.getProductName(),
                        data.getProductCategoryName(), data.getProductCategoryId(), data.getPrice());
                    productIndexService.indexProduct(toRequest(data));
                    log.info("✅ [CONSUMER] Successfully indexed product - ID: {}, Name: {}",
                        data.getProductId(), data.getProductName());
                }
                case PRODUCT_UPDATED -> {
                    log.info(
                        "🔄 [CONSUMER] Processing PRODUCT_UPDATED - ID: {}, Name: {}, Category: {} ({}), Price: {}",
                        data.getProductId(), data.getProductName(),
                        data.getProductCategoryName(), data.getProductCategoryId(), data.getPrice());
                    productIndexService.indexProduct(toRequest(data));
                    log.info("✅ [CONSUMER] Successfully updated product - ID: {}, Name: {}",
                        data.getProductId(), data.getProductName());
                }
                case PRODUCT_DELETED -> {
                    log.info("🗑️ [CONSUMER] Processing PRODUCT_DELETED event - Product ID: {}", data.getProductId());
                    productIndexService.deleteProduct(data.getProductId());
                    log.info("✅ [CONSUMER] Successfully deleted product - ID: {}", data.getProductId());
                }
                default -> {
                    log.warn("⚠️ [CONSUMER] Unknown event type received - Type: {}, Product ID: {}",
                        event.getType(), data.getProductId());
                }
            }
        } catch (Exception e) {
            log.error("❌ [CONSUMER] Failed to process product event - Type: {}, Product ID: {}, Name: {}, Error: {}",
                event.getType(), data.getProductId(), data.getProductName(), e.getMessage(), e);
            // 재시도 여부와 횟수는 KafkaConsumerConfig의 productEventListenerContainerFactory가 정한다.
            // 여기서 삼키면 그 정책이 동작하지 않으므로 그대로 다시 던진다.
            throw e;
        }
    }

    private ProductIndexRequest toRequest(ProductEvent.ProductEventData data) {
        return new ProductIndexRequest(
            data.getProductId(),
            data.getProductName(),
            data.getProductCategoryId(),
            data.getProductCategoryCode(),
            data.getProductCategoryName(),
            data.getPrice(),
            data.getStatus());
    }
}
