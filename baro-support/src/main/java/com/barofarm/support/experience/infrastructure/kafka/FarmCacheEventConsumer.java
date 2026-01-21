package com.barofarm.support.experience.infrastructure.kafka;

import com.barofarm.support.event.FarmEvent;
import com.barofarm.support.experience.infrastructure.cache.FarmCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Farm 이벤트를 구독하여 {@link FarmCacheService} 에 알리는 Consumer.
 *
 * 현재 구현에서는 FarmCacheService 쪽에서 Redis를 사용하지 않고,
 * 이벤트 수신 후 필요한 후처리 훅(hook)만 제공하는 용도로 사용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmCacheEventConsumer {

    private final FarmCacheService farmCacheService;

    @KafkaListener(topics = "farm-events", groupId = "support-service")
    public void onMessage(FarmEvent event) {
        FarmEvent.FarmEventData data = event.getData();
        log.info("📥 [CONSUMER] Farm event received - Type: {}, Farm ID: {}, Seller ID: {}",
            event.getType(), data.getFarmId(), data.getSellerId());

        try {
            switch (event.getType()) {
                case FARM_CREATED, FARM_UPDATED -> {
                    // Farm 생성/수정 시 캐시/권한 관련 후처리 훅 호출 (현재는 Redis 미사용)
                    farmCacheService.updateCache(data.getSellerId(), data.getFarmId());
                }
                case FARM_DELETED -> {
                    // Farm 삭제 시 캐시/권한 관련 후처리 훅 호출 (현재는 Redis 미사용)
                    farmCacheService.deleteCache(data.getSellerId(), data.getFarmId());
                }
                default -> {
                    // enum의 모든 케이스를 처리하므로 도달 불가능하지만 Checkstyle 요구사항 충족
                }
            }
        } catch (Exception e) {
            log.error("❌ [CONSUMER] Farm event 처리 실패 - Type: {}, Farm ID: {}, Error: {}",
                event.getType(), data.getFarmId(), e.getMessage(), e);
            // 예외를 다시 던져서 offset 커밋을 막고 재처리되도록 함
            // 주의: 무한 루프 방지를 위해 ErrorHandler 설정 필요
            throw new RuntimeException("Farm event 처리 실패", e);
        }
    }
}
