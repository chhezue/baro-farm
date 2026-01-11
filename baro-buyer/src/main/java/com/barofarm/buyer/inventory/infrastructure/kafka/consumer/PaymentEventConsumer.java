package com.barofarm.buyer.inventory.infrastructure.kafka.consumer;

import com.barofarm.buyer.common.exception.CustomException;
import com.barofarm.buyer.inventory.application.InventoryFacadeService;
import com.barofarm.buyer.inventory.application.dto.request.InventoryConfirmCommand;
import com.barofarm.buyer.inventory.domain.InventoryOutboxEvent;
import com.barofarm.buyer.inventory.domain.InventoryOutboxEventRepository;
import com.barofarm.buyer.inventory.exception.InventoryErrorCode;
import com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent;
import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryConfirmedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final InventoryFacadeService inventoryFacadeService;
    private final ObjectMapper objectMapper;
    private final InventoryOutboxEventRepository inventoryOutboxEventRepository;

    @KafkaListener(
        topics = "payment-confirmed",
        groupId = "inventory-service.payment-confirmed",
        properties = {
            "spring.json.value.default.type=com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent"
        }
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handle(PaymentConfirmedEvent event) {
        UUID orderId = event.orderId();
        System.out.println("payment-confirm!!!!!!!!!!!!!!!!!!!!!");
        // 1) 재고 확정 (로컬 트랜잭션)
        inventoryFacadeService.confirmInventory(InventoryConfirmCommand.of(orderId));

        // 2) 인벤토리 확정 이벤트를 Outbox에 적재
        InventoryConfirmedEvent dto = InventoryConfirmedEvent.from(event);
        try {
            String payload = objectMapper.writeValueAsString(dto);

            InventoryOutboxEvent outbox = InventoryOutboxEvent.pending(
                "INVENTORY",
                orderId.toString(),
                "inventory-confirmed",
                orderId.toString(),
                payload
            );
            inventoryOutboxEventRepository.save(outbox);
            System.out.println("payment-confirmㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂㅂ");
        } catch (JsonProcessingException e) {
            System.out.println("payment-confirmㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌㅌ");
            throw new CustomException(InventoryErrorCode.OUTBOX_SERIALIZATION_FAILED);
        }
    }
}

