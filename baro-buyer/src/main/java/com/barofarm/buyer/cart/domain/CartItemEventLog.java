package com.barofarm.buyer.cart.domain;

import com.barofarm.buyer.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Schema(description = "장바구니 항목 이벤트 로그")
@Getter
@Entity
@Table(name = "cart_item_event_log")
public class CartItemEventLog extends BaseEntity {

    @Schema(description = "이벤트 로그 UUID")
    @Id
    private UUID id;

    @Schema(description = "유저 UUID (로그인 사용자)")
    @Column(name = "buyer_id")
    private UUID buyerId;

    @Schema(description = "session key (비로그인 사용자)")
    @Column(name = "session_key")
    private String sessionKey;

    @Schema(description = "장바구니 항목 UUID")
    @Column(name = "cart_item_id")
    private UUID cartItemId;

    @Schema(description = "상품 UUID")
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Schema(description = "이벤트 타입 (ADDED, REMOVED, QUANTITY_CHANGED 등)")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private CartEventStatus eventType;

    @Schema(description = "이벤트 시점 기준 수량 또는 변경 후 수량")
    @Column(name = "quantity")
    private Integer quantity;

    @Schema(description = "옵션 정보(JSON)")
    @Column(name = "option_json")
    private String optionJson;

    @Schema(description = "이벤트 발생 시각")
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected CartItemEventLog() {
    }

    public static CartItemEventLog of(
        UUID buyerId,
        String sessionKey,
        UUID cartItemId,
        UUID productId,
        CartEventStatus eventType,
        Integer quantity,
        String optionJson,
        LocalDateTime occurredAt
    ) {
        CartItemEventLog log = new CartItemEventLog();
        log.id = UUID.randomUUID();
        log.buyerId = buyerId;
        log.sessionKey = sessionKey;
        log.cartItemId = cartItemId;
        log.productId = productId;
        log.eventType = eventType;
        log.quantity = quantity;
        log.optionJson = optionJson;
        log.occurredAt = occurredAt;
        return log;
    }
}
