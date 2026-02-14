package com.barofarm.shopping.product.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTransactionEventListener {

    private final ProductEventPublisher productEventPublisher;

    // DB 트랜잭션 성공 시에만 카프카 이벤트 발행됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductTransactionEvent(ProductTransactionEvent event) {
        log.info("🔄 [TRANSACTION_LISTENER] Transaction committed successfully. " +
                "Publishing Kafka event for Product ID: {}, Operation: {}",
                event.getProduct().getId(), event.getOperation());

        switch (event.getOperation()) {
            case CREATED -> {
                log.info("📤 [PRODUCT_SERVICE] Publishing PRODUCT_CREATED event to Kafka - " +
                        "Product ID: {}, Name: {}",
                        event.getProduct().getId(), event.getProduct().getProductName());
                productEventPublisher.publishProductCreated(event.getProduct());
            }
            case UPDATED -> {
                log.info("📤 [PRODUCT_SERVICE] Publishing PRODUCT_UPDATED event to Kafka - " +
                        "Product ID: {}, Name: {}",
                        event.getProduct().getId(), event.getProduct().getProductName());
                productEventPublisher.publishProductUpdated(event.getProduct());
            }
            case DELETED -> {
                log.info("📤 [PRODUCT_SERVICE] Publishing PRODUCT_DELETED event to Kafka - " +
                        "Product ID: {}, Name: {}",
                        event.getProduct().getId(), event.getProduct().getProductName());
                productEventPublisher.publishProductDeleted(event.getProduct());
            }
            default -> {
                log.warn("⚠️ [TRANSACTION_LISTENER] Unknown operation type: {}", event.getOperation());
            }
        }
    }

    // DB 트랜잭션 롤백 시에는 카프카 이벤트 발행되지 않음
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleProductTransactionRollback(ProductTransactionEvent event) {
        log.warn("⚠️ [TRANSACTION_LISTENER] Transaction rolled back. " +
                "Skipping Kafka event publishing for Product ID: {}, Operation: {}",
                event.getProduct().getId(), event.getOperation());
    }
}
