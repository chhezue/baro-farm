package com.barofarm.notification.notification_delivery.domain.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * 채널별 알림 전달 결과 모델.
 * 이벤트 단위 처리 결과를 운영/재시도 분석에 활용한다.
 *
 * 전송 결과 모델
 *
 * 실무에서 왜 필요하냐면:
 * - "이 이벤트는 EMAIL은 성공했는데 PUSH는 실패" 같은 상황이 흔함
 * - 이후 재시도/감사로그/운영 분석에서 큰 도움이 된다.
 */
public class DeliveryResult {

    private final String eventId;
    private final Instant processedAt;

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
