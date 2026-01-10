package com.barofarm.buyer.inventory.infrastructure.kafka.producer;

import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryConfirmEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, InventoryConfirmEvent> kafkaTemplate;

    public void send(InventoryConfirmEvent event){
        System.out.println("inventory-confirmed !!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        kafkaTemplate.send(
            "inventory-confirmed",
            event.orderId().toString(),
            event
        );
    }
}
