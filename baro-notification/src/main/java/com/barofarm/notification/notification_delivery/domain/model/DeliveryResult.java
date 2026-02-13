package com.barofarm.notification.notification_delivery.domain.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * ?꾩넚 寃곌낵 紐⑤뜽
 *
 * ?ㅻТ?먯꽌 ???꾩슂?섎깘硫?
 * - "???대깽?몃뒗 EMAIL? ?깃났?덈뒗??PUSH???ㅽ뙣" 媛숈? ?곹솴???뷀븿
 * - ?댄썑 ?ъ떆??媛먯궗濡쒓렇/?댁쁺 遺꾩꽍?먯꽌 ???꾩????쒕떎.
 */
public class DeliveryResult {

    private final String eventId;
    private final Instant processedAt;

    // 梨꾨꼸蹂?寃곌낵 ???(EMAIL->SUCCESS, PUSH->FAILED...)
    private final Map<DeliveryChannel, DeliveryStatus> statusByChannel = new EnumMap<>(DeliveryChannel.class);

    public DeliveryResult(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public void markSuccess(DeliveryChannel channel) {
        statusByChannel.put(channel, DeliveryStatus.SUCCESS);
    }

    public void markFailed(DeliveryChannel channel) {
        statusByChannel.put(channel, DeliveryStatus.FAILED);
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Map<DeliveryChannel, DeliveryStatus> getStatusByChannel() {
        return statusByChannel;
    }

    public enum DeliveryStatus {
        SUCCESS,
        FAILED
    }
}
