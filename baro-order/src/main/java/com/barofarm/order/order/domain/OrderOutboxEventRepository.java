package com.barofarm.order.order.domain;


public interface OrderOutboxEventRepository {
    OrderOutboxEvent save(OrderOutboxEvent orderOutboxEvent);
}
