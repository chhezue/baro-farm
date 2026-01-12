package com.barofarm.buyer.cart.application;

import com.barofarm.buyer.cart.application.dto.AddItemCommand;
import com.barofarm.buyer.cart.application.dto.CartInfo;
import com.barofarm.buyer.cart.domain.Cart;
import com.barofarm.buyer.cart.domain.CartItem;
import com.barofarm.buyer.cart.domain.CartRepository;
import com.barofarm.buyer.cart.exception.CartErrorCode;
import com.barofarm.buyer.common.exception.CustomException;
import com.barofarm.buyer.inventory.application.InventoryService;
import com.barofarm.buyer.product.application.ProductService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    /**
     * [장바구니 서비스]
     * - 현재 Product & Inventory가 같은 데이터소스 공유 중
     * - 분리 여부 불확실하며, 아마 분리하지 않을 것으로 판단: 같은 모듈 내 직접 호출
     * - 추후 분리 시 FeignClient 방식으로 변경 예정
     */
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CartRepository cartRepository;

    // 장바구니 조회
    public CartInfo getCart(UUID buyerId, String sessionKey) {
        if (buyerId != null) {
            return cartRepository.findByBuyerId(buyerId)
                .map(this::createCartInfoWithProductNames)
                .orElseGet(CartInfo::empty);
        } else if (sessionKey != null) {
            return cartRepository.findBySessionKey(sessionKey)
                .map(this::createCartInfoWithProductNames)
                .orElseGet(CartInfo::empty);
        } else {
            return CartInfo.empty();
        }
    }

    // 장바구니에 상품 추가
    @Transactional
    public CartInfo addItem(UUID buyerId, String sessionKey, AddItemCommand command) {
        // 1. 장바구니 조회 또는 신규 생성
        Cart cart = findOrCreateCart(buyerId, sessionKey);

        // 2. CartItem 생성
        CartItem item = CartItem.create(
            command.productId(),
            command.quantity(),
            command.unitPrice(),
            command.inventoryId()
        );

        // 새로운 아이템 저장
        cart.addItem(item);

        return createCartInfoWithProductNames(cart);
    }

    // 장바구니 항목 수량 변경
    @Transactional
    public CartInfo updateQuantity(UUID buyerId, String sessionKey, UUID itemId, int quantity) {
        Cart cart = findCart(buyerId, sessionKey);

        // 수량 변경
        Optional<CartItem> itemOpt = cart.findItem(itemId);
        if (itemOpt.isEmpty()) {
            throw new CustomException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
        CartItem item = itemOpt.get();
        cart.changeItemQuantity(item, quantity);

        return createCartInfoWithProductNames(cart);
    }

    // 장바구니 항목 옵션 변경
    @Transactional
    public CartInfo updateOption(UUID buyerId, String sessionKey, UUID itemId, UUID inventoryId) {
        Cart cart = findCart(buyerId, sessionKey);

        // 옵션 변경
        Optional<CartItem> itemOpt = cart.findItem(itemId);
        if (itemOpt.isEmpty()) {
            throw new CustomException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
        CartItem item = itemOpt.get();
        cart.changeItemOption(item, inventoryId);

        return createCartInfoWithProductNames(cart);
    }

    // 장바구니 항목 삭제
    @Transactional
    public void removeItem(UUID buyerId, String sessionKey, UUID itemId) {
        Cart cart = findCart(buyerId, sessionKey);
        cart.removeItem(itemId); // 장바구니 항목 삭제
    }

    // 장바구니 전체 삭제
    @Transactional
    public void clearCart(UUID buyerId, String sessionKey) {
        Cart cart = findCart(buyerId, sessionKey);
        cart.clear(); // 장바구니 전체 항목 삭제
    }

    // 비로그인 사용자 로그인 시 장바구니 병합
    @Transactional
    public CartInfo mergeCart(UUID buyerId, String sessionKey) {
        // 1. 비로그인 사용자의 장바구니를 세션 키로 조회 (없으면 buyerId로 로그인한 사용자의 장바구니 반환)
        Optional<Cart> guestOpt = cartRepository.findBySessionKey(sessionKey);
        if (guestOpt.isEmpty()) {
            return getCart(buyerId, sessionKey);
        }
        Cart guestCart = guestOpt.get();

        // 2. User Cart 조회 or 신규 생성
        Cart userCart = cartRepository.findByBuyerId(buyerId)
            .orElseGet(() -> cartRepository.save(Cart.create(buyerId)));

        // 3. Guest Cart 항목 병합 후 자동 저장
        for (CartItem item : guestCart.getItems()) {
            CartItem copied = CartItem.create(
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getInventoryId()
            );
            userCart.addItem(copied);
        }

        // 4. Guest Cart 삭제
        cartRepository.delete(guestCart);

        return createCartInfoWithProductNames(userCart);
    }

    /* ====== 내부 헬퍼 메소드 ====== */

    // 장바구니 조회 시, 실시간 상품명으로 CartInfo 생성하여 반환
    // TODO: N+1 문제 해결
    private CartInfo createCartInfoWithProductNames(Cart cart) {
        var productIds = cart.getItems().stream()
            .map(CartItem::getProductId)
            .distinct()
            .toList();

        Map<UUID, String> productNameMap = new HashMap<>();

        for (UUID id : productIds) {
            try {
                var product = productService.getProductDetail(id);
                productNameMap.put(id, product.productName());
            } catch (Exception e) {
                productNameMap.put(id, null);
            }
        }

        return CartInfo.from(cart, productNameMap);
    }

    // 기존 장바구니 조회 (없으면 예외)
    private Cart findCart(UUID buyerId, String sessionKey) {
        if (buyerId != null) {
            return cartRepository.findByBuyerId(buyerId)
                .orElseThrow(() -> new CustomException(CartErrorCode.CART_NOT_FOUND));
        } else if (sessionKey != null) {
            return cartRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new CustomException(CartErrorCode.CART_NOT_FOUND));
        }
        throw new CustomException(CartErrorCode.BUYER_ID_OR_SESSION_KEY_REQUIRED);
    }

    // 장바구니 조회 또는 신규 생성
    private Cart findOrCreateCart(UUID buyerId, String sessionKey) {
        if (buyerId != null) {
            return cartRepository.findByBuyerId(buyerId)
                .orElseGet(() -> cartRepository.save(Cart.create(buyerId)));
        } else if (sessionKey != null) {
            return cartRepository.findBySessionKey(sessionKey)
                .orElseGet(() -> cartRepository.save(Cart.createForGuest(sessionKey)));
        }
        throw new CustomException(CartErrorCode.BUYER_ID_OR_SESSION_KEY_REQUIRED);
    }
}
