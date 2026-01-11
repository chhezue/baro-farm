package com.barofarm.support.deposit.infrastructure.kafka;

import com.barofarm.support.deposit.infrastructure.kafka.dto.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderPaidEvent> kafkaTemplate;

    public void send(OrderPaidEvent event){
        kafkaTemplate.send(
            "order-paid",
            event.orderId().toString(),
            event
        );
    }
}
