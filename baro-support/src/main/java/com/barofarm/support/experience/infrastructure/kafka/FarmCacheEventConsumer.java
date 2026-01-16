package com.barofarm.support.experience.infrastructure.kafka;

import com.barofarm.support.event.FarmEvent;
import com.barofarm.support.experience.infrastructure.cache.FarmCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Farm 이벤트를 구독하여 Redis 캐시를 업데이트하는 Consumer */
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
                    // Redis 캐시 업데이트 (Set에 farmId 추가)
                    farmCacheService.updateCache(data.getSellerId(), data.getFarmId());
                }
                case FARM_DELETED -> {
                    // Redis 캐시에서 특정 farmId 제거
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
