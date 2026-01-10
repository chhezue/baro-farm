package com.barofarm.order.order.application;

import static com.barofarm.order.order.exception.OrderErrorCode.ORDER_ACCESS_DENIED;
import static com.barofarm.order.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import com.barofarm.order.common.exception.CustomException;
import com.barofarm.order.common.response.CustomPage;
import com.barofarm.order.common.response.ResponseDto;
import com.barofarm.order.order.application.dto.request.OrderCreateCommand;
import com.barofarm.order.order.application.dto.response.*;
//import com.barofarm.order.order.application.dto.response.OrderDeliveryInfo;
//import com.barofarm.order.order.application.dto.response.OrderDetailInfo;
//import com.barofarm.order.order.client.DeliveryClient;

import com.barofarm.order.order.domain.*;
import com.barofarm.order.order.infrastructure.rest.InventoryClient;
import com.barofarm.order.order.infrastructure.rest.dto.InventoryCancelRequest;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryClient inventoryClient;
    private final CompensationRegistryRepository compensationRegistryRepository;

    @Transactional
    public OrderCreateInfo createOrder(UUID userId, OrderCreateCommand command){

        Order order = Order.of(userId, command);

        for (OrderCreateCommand.OrderItemCreateCommand item : command.items()) {
            order.addOrderItem(
                item.productId(),
                item.productName(),
                item.sellerId(),
                item.quantity(),
                item.unitPrice(),
                item.inventoryId()
            );
        }

        Order saved = orderRepository.save(order);
        return OrderCreateInfo.from(saved);
    }

    public void compensateInventory(UUID orderId){
        try{
            markFailed(orderId);
            InventoryCancelRequest cancelRequest = new InventoryCancelRequest(
                orderId
            );
            inventoryClient.cancelInventory(cancelRequest);

        } catch (Exception e) {
            // 스케줄러(배치) : CompensationRegistry에 PENDING 상태로 있는 orderId에 대해 만약 inventoryReservation에
            // 재고가 RESERVED 상태면 재고를 RELEASE 그리고 order를 PENDING에서 FAILED로 변경
            compensationRegistryRepository.save(CompensationRegistry.of(orderId));
        }
    }

    @Transactional
    public void markInventoryReserved(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));

        order.markInventoryReserved();
        System.out.println("mir"+order.getStatus());
    }

    @Transactional
    public void markFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));

        order.markFailed();
        System.out.println("mf"+order.getStatus());
    }

    @Transactional(readOnly = true)
    public ResponseDto<OrderDetailInfo> findOrderDetail(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));

        validateOwner(userId, order);
        return ResponseDto.ok(OrderDetailInfo.from(order));
    }

    @Transactional(readOnly = true)
    public ResponseDto<CustomPage<OrderDetailInfo>> findOrderList(UUID userId, Pageable pageable){
        Page<OrderDetailInfo> page = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(OrderDetailInfo::from);
        return ResponseDto.ok(CustomPage.from(page));
    }

//    @Transactional
//    public ResponseDto<OrderCancelInfo> cancelOrder(UUID userId, UUID orderId) {
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));
//
//        validateOwner(userId, order);
//
//        if (order.isCanceled()) {
//            return ResponseDto.ok(OrderCancelInfo.from(order));
//        }
//
//        if (!order.getStatus().isCancelable()) {
//            throw new CustomException(ORDER_NOT_CANCELABLE_STATUS);
//        }
//        order.markCancel();
//
//        InventoryCancelRequest inventoryCancelRequest = new InventoryCancelRequest(
//            order.getId()
//        );
//        inventoryClient.cancelInventory(inventoryCancelRequest);
//        return ResponseDto.ok(OrderCancelInfo.from(order));
//    }
//
//    @Transactional(readOnly = true)
//    public OrderDeliveryInfo getDeliveryInfo(UUID orderId) {
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new CustomException(ORDER_NOT_FOUND));
//
//        if (order.getStatus() != PAID) {
//            throw new CustomException(INVALID_ORDER_STATUS_FOR_DELIVERY);
//        }
//        return OrderDeliveryInfo.from(order);
//    }
//

//    @Transactional(readOnly = true)
//    public CustomPage<OrderItemSettlementResponse> findOrderItemsForSettlement(
//        LocalDate startDate, LocalDate endDate, Pageable pageable) {
//        LocalDateTime startDateTime = startDate.atStartOfDay();
//        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay().minusNanos(1);
//
//        return orderItemRepository.findOrderItemsForSettlement(startDateTime, endDateTime, pageable);
//    }
//
//    @Transactional(readOnly = true)
//    public OrderItemInternalResponse getOrderItem(UUID orderItemId) {
//        OrderItem orderItem = orderItemRepository.findById(orderItemId)
//                .orElseThrow(() -> new CustomException(ORDER_ITEM_NOT_FOUND));
//
//        return OrderItemInternalResponse.from(orderItem);
//    }

    private void validateOwner(UUID userId, Order order) {
        if (!order.getUserId().equals(userId)) {
            throw new CustomException(ORDER_ACCESS_DENIED);
        }
    }
}
