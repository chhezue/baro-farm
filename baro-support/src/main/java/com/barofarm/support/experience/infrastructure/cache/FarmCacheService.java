package com.barofarm.support.experience.infrastructure.cache;

import com.barofarm.support.common.client.FarmClient;
import com.barofarm.support.common.client.dto.CustomPage;
import com.barofarm.support.common.client.dto.FarmDetailInfo;
import com.barofarm.support.common.client.dto.FarmListInfo;
import com.barofarm.support.common.client.dto.FarmResponseDto;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Farm 정보를 Redis에 캐싱하는 서비스 (userId → farmId 매핑) */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmCacheService {

    // Redis 캐시는 더 이상 사용하지 않고, Farm 서비스 조회(Feign)만 사용합니다.
    private final FarmClient farmClient;

    /**
     * 사용자가 특정 farm을 소유하고 있는지 확인
     * 1. Redis Set에서 먼저 확인 (빠름)
     * 2. 없으면 GET /{id} API로 확인 (fallback)
     *
     * @param userId 사용자 ID (sellerId)
     * @param farmId 확인할 farm ID
     * @return 소유하고 있으면 true, 없으면 false
     */
    public boolean hasFarmAccess(UUID userId, UUID farmId) {
        if (farmId == null || userId == null) {
            return false;
        }

        // Redis 캐시 대신, 항상 GET /{id} API로 확인합니다.
        try {
            FarmResponseDto<FarmDetailInfo> response = farmClient.getFarmById(farmId);

            if (response != null && response.data() != null) {
                // farm의 sellerId와 userId 비교
                UUID sellerId = response.data().sellerId();
                boolean hasAccess = userId.equals(sellerId);

                return hasAccess;
            }
        } catch (FeignException e) {
            if (e.status() == 404) {
                // farm이 존재하지 않음
                return false;
            }
            log.warn("⚠️ [CACHE] Farm 접근 권한 확인 실패 - userId: {}, farmId: {}, error: {}",
                userId, farmId, e.getMessage());
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Farm 접근 권한 확인 중 오류 - userId: {}, farmId: {}, error: {}",
                userId, farmId, e.getMessage());
        }

        return false;
    }

    /**
     * 사용자가 특정 farm을 소유하고 있는지 확인하고 반환
     * farmId가 null이면 첫 번째 farm을 반환
     *
     * @param userId 사용자 ID
     * @param farmId 확인할 farm ID (null이면 첫 번째 farm 반환)
     * @return 소유한 farm ID 또는 null
     */
    public UUID getFarmIdByUserId(UUID userId, UUID farmId) {
        // farmId가 지정된 경우, 해당 farm을 소유하고 있는지 확인
        if (farmId != null) {
            if (hasFarmAccess(userId, farmId)) {
                return farmId;
            }
            // 소유하지 않은 farm이면 null 반환
            return null;
        }

        // farmId가 null이면 첫 번째 farm 반환
        return getFirstFarmIdByUserId(userId);
    }

    /**
     * 사용자 ID로 첫 번째 농장 ID 조회 (하이브리드 방식)
     * 주의: 사용자가 여러 farm을 소유할 수 있으므로, 첫 번째 farm만 반환합니다.
     * 특정 farm에 대한 권한 확인이 필요한 경우 {@link #getFarmIdByUserId(UUID, UUID)}를 사용하세요.
     *
     * @param userId 사용자 ID
     * @return 농장 ID (첫 번째 farm) 또는 null
     */
    private UUID getFirstFarmIdByUserId(UUID userId) {
        // Redis 대신, 항상 Feign으로 조회합니다.
        try {
            // /me 엔드포인트를 사용하여 모든 farm 조회 (큰 사이즈로 요청)
            PageRequest pageRequest = PageRequest.of(0, 100); // 최대 100개까지
            FarmResponseDto<CustomPage<FarmListInfo>> response =
                farmClient.getMyFarmList(userId, pageRequest);

            // 응답에서 모든 farm의 ID 추출
            UUID firstFarmId = null;

            if (response != null && response.data() != null
                && response.data().content() != null
                && !response.data().content().isEmpty()) {

                for (FarmListInfo farm : response.data().content()) {
                    if (firstFarmId == null) {
                        firstFarmId = farm.id(); // 첫 번째 farm 저장
                    }
                }
            }

            return firstFarmId;
        } catch (FeignException e) {
            if (e.status() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Farm 이벤트 수신 시 Redis 캐시 업데이트
     * Farm 생성/수정 시 Set에 추가
     *
     * @param userId 사용자 ID (sellerId)
     * @param farmId 농장 ID
     */
    public void updateCache(UUID userId, UUID farmId) {
        // Redis 캐시 미사용: 이벤트는 로깅만 수행
        log.info("🔄 [CACHE] (NO-OP) Redis 캐시 비활성화 상태 - updateCache 호출 userId: {}, farmId: {}", userId, farmId);
    }

    /**
     * Farm 삭제 시 Redis 캐시에서 제거
     * 특정 farmId를 Set에서 제거
     *
     * @param userId 사용자 ID (sellerId)
     * @param farmId 삭제할 농장 ID
     */
    public void deleteCache(UUID userId, UUID farmId) {
        // Redis 캐시 미사용: 이벤트는 로깅만 수행
        log.info("🗑️ [CACHE] (NO-OP) Redis 캐시 비활성화 상태 - deleteCache(userId, farmId) 호출 userId: {}, farmId: {}",
            userId, farmId);
    }

    /**
     * Farm 삭제 시 Redis 캐시 삭제 (하위 호환성)
     * 모든 farm 캐시 삭제
     *
     * @param userId 사용자 ID (sellerId)
     */
    public void deleteCache(UUID userId) {
        // Redis 캐시 미사용: 이벤트는 로깅만 수행
        log.info("🗑️ [CACHE] (NO-OP) Redis 캐시 비활성화 상태 - deleteCache(userId) 호출 userId: {}", userId);
    }
}
