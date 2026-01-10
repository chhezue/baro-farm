package com.barofarm.buyer.inventory.infrastructure.kafka.producer;

import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryConfirmFailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventFailProducer {

    private final KafkaTemplate<String, InventoryConfirmFailEvent> kafkaTemplate;

    public void send(InventoryConfirmFailEvent event){
        System.out.println("inventory-confirmed failed!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        kafkaTemplate.send(
            "inventory-confirmed-fail",
            event.orderId().toString(),
            event
        );
    }
}
