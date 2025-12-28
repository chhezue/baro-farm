package com.barofarm.seller.farm.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmEvent {

    private FarmEventType type;
    private FarmEventData data;

    public enum FarmEventType {
        FARM_CREATED,
        FARM_UPDATED,
        FARM_DELETED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmEventData {
        private UUID farmId;
        /**
         * 판매자 ID (sellerId = userId)
         * baro-support의 FarmCacheService가 Redis에 "farm:user:{sellerId}" = "{farmId}" 형태로 저장하기 위해 필요
         * ExperienceService에서 userId로 farmId를 조회할 때 사용됨
         */
        private UUID sellerId;
        private String farmName;
        private String farmAddress;
        private String status;
        private Instant updatedAt;
    }
}
