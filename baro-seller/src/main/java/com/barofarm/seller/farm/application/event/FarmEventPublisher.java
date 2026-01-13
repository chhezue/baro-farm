package com.barofarm.seller.farm.application.event;

import com.barofarm.seller.farm.domain.Farm;
import com.barofarm.seller.farm.event.FarmEvent;
import com.barofarm.seller.farm.event.FarmEvent.FarmEventType;
import com.barofarm.seller.farm.infrastructure.kafka.FarmEventProducer;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Farm 도메인을 Kafka 이벤트로 변환하고, 어떤 이벤트를 발행할지 결정 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmEventPublisher {

    private final FarmEventProducer producer;

    /**
     * Farm 생성 시 발행
     *
     * @param farm Farm 엔티티
     */
    public void publishFarmCreated(Farm farm) {
        log.info(
            "📨 [EVENT_PUBLISHER] Building FARM_CREATED - Farm ID: {}, Seller ID: {}",
            farm.getId(), farm.getSeller().getId());

        FarmEvent event = buildEvent(FarmEventType.FARM_CREATED, farm);
        log.info("📨 [EVENT_PUBLISHER] Event built successfully - Type: {}, Farm ID: {}",
            event.getType(), event.getData().getFarmId());

        producer.send(event);
    }

    /**
     * Farm 수정 시 발행
     *
     * @param farm Farm 엔티티
     */
    public void publishFarmUpdated(Farm farm) {
        log.info(
            "📨 [EVENT_PUBLISHER] Building FARM_UPDATED - Farm ID: {}, Seller ID: {}",
            farm.getId(), farm.getSeller().getId());

        FarmEvent event = buildEvent(FarmEventType.FARM_UPDATED, farm);
        producer.send(event);
    }

    /**
     * Farm 삭제 시 발행
     *
     * @param farm Farm 엔티티
     */
    public void publishFarmDeleted(Farm farm) {
        log.info(
            "📨 [EVENT_PUBLISHER] Building FARM_DELETED - Farm ID: {}, Seller ID: {}",
            farm.getId(), farm.getSeller().getId());

        FarmEvent event = buildEvent(FarmEventType.FARM_DELETED, farm);
        producer.send(event);
    }

    private FarmEvent buildEvent(FarmEventType type, Farm farm) {
        FarmEvent.FarmEventData data = FarmEvent.FarmEventData.builder()
            .farmId(farm.getId())
            .sellerId(farm.getSeller().getId())
            .farmName(farm.getName())
            .farmAddress(farm.getAddress())
            .status(farm.getStatus().name())
            .updatedAt(farm.getUpdatedAt() != null
                ? farm.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now())
            .build();

        return FarmEvent.builder()
            .type(type)
            .data(data)
            .build();
    }
}
