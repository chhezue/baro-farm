package com.barofarm.buyer.inventory.infrastructure.kafka.consumer;

import com.barofarm.buyer.common.exception.CustomException;
import com.barofarm.buyer.inventory.application.InventoryFacadeService;
import com.barofarm.buyer.inventory.application.dto.request.InventoryCancelCommand;
import com.barofarm.buyer.inventory.domain.InventoryOutboxEvent;
import com.barofarm.buyer.inventory.domain.InventoryOutboxEventRepository;
import com.barofarm.buyer.inventory.exception.InventoryErrorCode;
import com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.OrderCancelRequestedEvent;
import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryCanceledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderCancelRequestedConsumer {

    private final InventoryFacadeService inventoryFacadeService;
    private final InventoryOutboxEventRepository inventoryOutboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-cancel-requested",
        groupId = "inventory-service.order-cancel-requested",
        properties = {
            "spring.json.value.default.type=com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.OrderCancelRequestedEvent"
        }
    )
    @Transactional
    public void handle(OrderCancelRequestedEvent event) {
        inventoryFacadeService.cancelInventory(InventoryCancelCommand.of(event.orderId()));

        InventoryCanceledEvent canceledEvent = new InventoryCanceledEvent(event.orderId());
        try {
            String payload = objectMapper.writeValueAsString(canceledEvent);
            InventoryOutboxEvent outbox = InventoryOutboxEvent.pending(
                "INVENTORY",
                event.orderId().toString(),
                "inventory-canceled",
                event.orderId().toString(),
                payload
            );
            inventoryOutboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            // 취소 SAGA에서 outbox 직렬화 실패는 재시도/모니터링 대상
            throw new CustomException(InventoryErrorCode.OUTBOX_SERIALIZATION_FAILED);
        }
    }
}

