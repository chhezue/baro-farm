package com.barofarm.buyer.inventory.infrastructure.kafka.producer;

import com.barofarm.buyer.inventory.infrastructure.kafka.producer.dto.InventoryConfirmedFailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventFailProducer {

    private final KafkaTemplate<String, InventoryConfirmedFailEvent> kafkaTemplate;

    public void send(InventoryConfirmedFailEvent event){
        System.out.println("inventory-confirmed failed!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        kafkaTemplate.send(
            "inventory-confirmed-fail",
            event.orderId().toString(),
            event
        );
    }
}
