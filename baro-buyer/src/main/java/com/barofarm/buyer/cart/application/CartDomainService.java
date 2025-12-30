package com.barofarm.buyer.cart.application;

import com.barofarm.buyer.cart.domain.Cart;
import com.barofarm.buyer.cart.domain.CartEventStatus;
import com.barofarm.buyer.cart.domain.CartItem;
import com.barofarm.buyer.cart.domain.CartItemEventLog;
import com.barofarm.buyer.cart.domain.CartItemEventLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartDomainService {

    private final CartItemEventLogRepository eventLogRepository;

    /**
     * Cart에 CartItem 추가 + 이벤트 로그 남기기 (best-effort)
     */
    public void addItemAndLog(Cart cart, CartItem newItem, UUID buyerId, String sessionKey) {
        cart.addItem(newItem);
        saveEventLogBestEffort(buyerId, sessionKey, newItem, CartEventStatus.ADDED);
    }

    /**
     * CartItem 수량 변경 + 이벤트 로그 남기기
     */
    public boolean updateQuantityAndLog(Cart cart, UUID cartItemId, int newQty, UUID buyerId, String sessionKey) {
        // 수량을 변경할 상품이 없으면 return false
        Optional<CartItem> itemOpt = cart.findItem(cartItemId);
        if (itemOpt.isEmpty()) {
            return false;
        }

        CartItem item = itemOpt.get();
        cart.changeItemQuantity(item, newQty);

        saveEventLogBestEffort(
            buyerId,
            sessionKey,
            item.getId(),
            item.getProductId(),
            CartEventStatus.QUANTITY_CHANGED,
            newQty,
            item.getOptionInfoJson()
        );

        return true;
    }

    /**
     * CartItem 옵션 변경 + 이벤트 로그 남기기
     */
    public boolean updateOptionAndLog(Cart cart, UUID cartItemId, String newOptionJson,
                                      UUID buyerId, String sessionKey) {
        Optional<CartItem> itemOpt = cart.findItem(cartItemId);
        if (itemOpt.isEmpty()) {
            return false;
        }

        CartItem item = itemOpt.get();
        cart.changeItemOption(item, newOptionJson);

        saveEventLogBestEffort(
            buyerId,
            sessionKey,
            item.getId(),
            item.getProductId(),
            CartEventStatus.OPTION_CHANGED,
            item.getQuantity(),
            newOptionJson
        );

        return true;
    }

    /**
     * CartItem 삭제 + 이벤트 로그 남기기
     */
    public void removeItemAndLog(Cart cart, UUID cartItemId, UUID buyerId, String sessionKey) {
        Optional<CartItem> itemOpt = cart.findItem(cartItemId);
        if (itemOpt.isEmpty()) {
            return;
        }

        CartItem item = itemOpt.get();
        cart.removeItem(cartItemId);

        saveEventLogBestEffort(
            buyerId,
            sessionKey,
            item.getId(),
            item.getProductId(),
            CartEventStatus.REMOVED,
            item.getQuantity(),
            item.getOptionInfoJson()
        );
    }

    /**
     * 장바구니 전체 비우기 + 각 아이템에 대해 이벤트 로그 남기기
     */
    public void clearCartAndLog(Cart cart, UUID buyerId, String sessionKey) {
        // 삭제 전에 아이템 정보 복사 (clear 후에는 조회 불가)
        List<CartItem> itemsToLog = List.copyOf(cart.getItems());

        cart.clear();

        // 각 아이템에 대해 REMOVED 이벤트 로그 (best-effort)
        for (CartItem item : itemsToLog) {
            saveEventLogBestEffort(
                buyerId,
                sessionKey,
                item.getId(),
                item.getProductId(),
                CartEventStatus.REMOVED,
                item.getQuantity(),
                item.getOptionInfoJson()
            );
        }
    }

    /* ====== 내부 헬퍼 메소드 ====== */

    /**
     * 이벤트 로그 저장 (best-effort 방식)
     * 로그 저장 실패해도 장바구니 비즈니스 로직은 계속 진행
     */
    private void saveEventLogBestEffort(UUID buyerId, String sessionKey, CartItem item, CartEventStatus eventType) {
        saveEventLogBestEffort(
            buyerId,
            sessionKey,
            item.getId(),
            item.getProductId(),
            eventType,
            item.getQuantity(),
            item.getOptionInfoJson()
        );
    }

    /**
     * 이벤트 로그 저장 (best-effort 방식 - 별도 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEventLogBestEffort(
        UUID buyerId,
        String sessionKey,
        UUID cartItemId,
        UUID productId,
        CartEventStatus eventType,
        Integer quantity,
        String optionJson
    ) {
        try {
            CartItemEventLog log = CartItemEventLog.of(
                buyerId,
                sessionKey,
                cartItemId,
                productId,
                eventType,
                quantity,
                optionJson,
                LocalDateTime.now()
            );
            eventLogRepository.save(log);
        } catch (Exception e) {
            // 로그 저장 실패해도 장바구니 기능은 계속 동작
            // 나중에 배치로 복구하거나 모니터링으로 대응 가능
            log.warn("Failed to save cart event log: buyerId={}, sessionKey={}, eventType={}, productId={}",
                buyerId, sessionKey, eventType, productId, e);
        }
    }
}
