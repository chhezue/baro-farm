package com.barofarm.order.order.infrastructure.kafka.producer;

import com.barofarm.order.order.infrastructure.kafka.producer.dto.OrderConfirmedFailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventFailProducer {

    private final KafkaTemplate<String, OrderConfirmedFailEvent> kafkaTemplate;

    public void send(OrderConfirmedFailEvent event){
        System.out.println("order-paid !!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        kafkaTemplate.send(
            "order-confirmed-fail",
            event.orderId().toString(),
            event
        );
    }
}
