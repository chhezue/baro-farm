package com.barofarm.order.order.presentation;

import com.barofarm.order.common.response.CustomPage;
import com.barofarm.order.common.response.ResponseDto;
import com.barofarm.order.order.application.OrderOrchestrator;
import com.barofarm.order.order.application.OrderService;
import com.barofarm.order.order.application.dto.response.OrderCancelInfo;
import com.barofarm.order.order.application.dto.response.OrderCreateInfo;
import com.barofarm.order.order.application.dto.response.OrderDetailInfo;
import com.barofarm.order.order.presentation.dto.OrderCreateRequest;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.v1}/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final OrderOrchestrator orderOrchestrator;

    @PostMapping
    public ResponseDto<OrderCreateInfo> placeOrder(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody OrderCreateRequest request) {
        return orderOrchestrator.placeOrder(userId, request.toCommand());
    }

    @GetMapping("/{orderId}")
    public ResponseDto<OrderDetailInfo> findOrderDetail(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID orderId) {
        return orderService.findOrderDetail(userId, orderId);
    }

    @GetMapping
    public ResponseDto<CustomPage<OrderDetailInfo>> findOrderList(
        @RequestHeader("X-User-Id") UUID userId,
        Pageable pageable) {
        return orderService.findOrderList(userId, pageable);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseDto<OrderCancelInfo> cancelOrder(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID orderId) {
        return orderService.cancelOrder(userId, orderId);
    }
}
