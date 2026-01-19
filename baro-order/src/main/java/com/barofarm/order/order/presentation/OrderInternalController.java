package com.barofarm.order.order.presentation;

import com.barofarm.order.order.application.OrderService;
import com.barofarm.order.order.application.dto.response.OrderItemInternalResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderService orderService;

    @GetMapping("/internal/order-items/{id}")
    public OrderItemInternalResponse getOrderItem(@PathVariable("id") UUID orderItemId) {
        return orderService.getOrderItem(orderItemId);
    }
}
