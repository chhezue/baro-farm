package com.barofarm.order.order.infrastructure.kafka.producer;

import com.barofarm.order.order.infrastructure.kafka.producer.dto.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderConfirmedEvent> kafkaTemplate;

    public void send(OrderConfirmedEvent event){
        System.out.println("order-paid !!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        kafkaTemplate.send(
            "order-confirmed",
            event.orderId().toString(),
            event
        );
    }
}
