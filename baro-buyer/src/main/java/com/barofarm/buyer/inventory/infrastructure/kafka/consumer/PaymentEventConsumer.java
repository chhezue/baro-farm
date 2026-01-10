package com.barofarm.buyer.inventory.infrastructure.kafka.consumer;

import com.barofarm.buyer.inventory.application.InventoryFacadeService;
import com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent;
import com.barofarm.buyer.inventory.application.dto.request.InventoryConfirmCommand;
import com.barofarm.buyer.inventory.infrastructure.kafka.producer.InventoryEventProducer;
import com.barofarm.buyer.inventory.infrastructure.kafka.producer.InventoryEventFailProducer;
import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryConfirmEvent;
import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryConfirmFailEvent;
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
    private final InventoryEventProducer inventoryEventProducer;
    private final InventoryEventFailProducer inventoryEventFailProducer;

    @KafkaListener(
        topics = "payment-confirmed",
        groupId = "inventory-service.payment-confirmed",
        properties = {
            "spring.json.value.default.type=com.barofarm.buyer.inventory.infrastructure.kafka.consumer.dto.PaymentConfirmedEvent"
        }
    )
    @RetryableTopic(
        // 총 시도 횟수 (최초 시도 1회 + 재시도 4회)
        attempts = "5",
        // 재시도 간격 (1000ms -> 2000ms -> 4000ms -> 8000ms 순으로 재시도 시간이 증가한다.)
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handle(PaymentConfirmedEvent event){
        UUID orderId = event.orderId();
        System.out.println("payment-confirmed !!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        try{
            inventoryFacadeService.confirmInventory(InventoryConfirmCommand.of(event.orderId()));

            inventoryEventProducer.send(InventoryConfirmEvent.from(event));
        }

        catch (Exception e){
            inventoryEventFailProducer.send(InventoryConfirmFailEvent.from(event));
        }
    }
}
