package com.barofarm.order.order.presentation;

import com.barofarm.order.common.response.CustomPage;
import com.barofarm.order.common.response.ResponseDto;
import com.barofarm.order.order.application.OrderOrchestrator;
import com.barofarm.order.order.application.OrderService;
import com.barofarm.order.order.application.dto.response.OrderCancelInfo;
import com.barofarm.order.order.application.dto.response.OrderCreateInfo;
//import com.barofarm.order.order.application.dto.response.OrderDetailInfo;
import com.barofarm.order.order.application.dto.response.OrderDetailInfo;
import com.barofarm.order.order.presentation.dto.OrderCreateRequest;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.v1}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderOrchestrator orderOrchestrator;

    // 완료
    @PostMapping
    public ResponseDto<OrderCreateInfo> createOrder(
        //@RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody OrderCreateRequest request) {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        return orderOrchestrator.placeOrder(userId, request.toCommand());
    }

    // 완료
    @GetMapping("/{orderId}")
    public ResponseDto<OrderDetailInfo> findOrderDetail(
        //@RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID orderId) {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        return orderService.findOrderDetail(userId, orderId);
    }

    // 완료
    @GetMapping
    public ResponseDto<CustomPage<OrderDetailInfo>> findOrderList(
        //@RequestHeader("X-User-Id") UUID userId,
        Pageable pageable) {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        return orderService.findOrderList(userId, pageable);
    }

    // 필요한가에 대한 의문
//    @PutMapping("/{orderId}/cancel")
//    public ResponseDto<OrderCancelInfo> cancelOrder(
//        //@RequestHeader("X-User-Id") UUID userId,
//        @PathVariable UUID orderId) {
//        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
//        return orderService.cancelOrder(userId, orderId);
//    }
}
