package com.barofarm.support.experience.infrastructure.cache;

import com.barofarm.support.common.client.FarmClient;
import com.barofarm.support.common.client.dto.CustomPage;
import com.barofarm.support.common.client.dto.FarmDetailInfo;
import com.barofarm.support.common.client.dto.FarmListInfo;
import com.barofarm.support.common.client.dto.FarmResponseDto;
import feign.FeignException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** Farm 정보를 Redis에 캐싱하는 서비스 (userId → farmId 매핑) */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final FarmClient farmClient;

    private static final String FARM_KEY_PREFIX = "farm:user:";
    private static final String FARM_SET_KEY_PREFIX = "farm:user:set:";
    private static final Duration TTL = Duration.ofDays(7); // Farm 정보는 자주 변경되지 않음

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

        String setKey = FARM_SET_KEY_PREFIX + userId;

        try {
            // 1. Redis Set에서 먼저 확인 (빠름)
            Boolean isMember = redisTemplate.opsForSet().isMember(setKey, farmId.toString());
            if (Boolean.TRUE.equals(isMember)) {
                log.debug("✅ [CACHE] Farm access confirmed in Redis Set - userId: {}, farmId: {}", userId, farmId);
                return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Redis Set 조회 실패, API로 fallback - userId: {}, farmId: {}, error: {}", 
                userId, farmId, e.getMessage());
        }

        // 2. Redis에 없으면 GET /{id} API로 확인 (fallback)
        try {
            FarmResponseDto<FarmDetailInfo> response = farmClient.getFarmById(farmId);

            if (response != null && response.data() != null) {
                // farm의 sellerId와 userId 비교
                UUID sellerId = response.data().sellerId();
                boolean hasAccess = userId.equals(sellerId);

                // API로 확인한 결과를 Redis Set에 저장 (다음 요청을 위해)
                if (hasAccess) {
                    try {
                        redisTemplate.opsForSet().add(setKey, farmId.toString());
                        redisTemplate.expire(setKey, TTL);
                        log.debug("💾 [CACHE] Farm ID added to Redis Set - userId: {}, farmId: {}", userId, farmId);
                    } catch (Exception e) {
                        log.warn("⚠️ [CACHE] Redis Set 저장 실패 - userId: {}, farmId: {}, error: {}", 
                            userId, farmId, e.getMessage());
                    }
                }

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
        String setKey = FARM_SET_KEY_PREFIX + userId;

        try {
            // 1. Redis Set에서 하나의 farm 조회
            // Set<String> farmIds = redisTemplate.opsForSet().members(setKey);
            // members()는 O(N)이므로 randomMember()를 사용하여 O(1)로 개선
            String farmIdStr = redisTemplate.opsForSet().randomMember(setKey);
            if (farmIdStr != null) {
                // UUID firstFarmId = UUID.fromString(farmIds.iterator().next());
                UUID firstFarmId = UUID.fromString(farmIdStr);
                log.debug("✅ [CACHE] First farm ID found in Redis Set - userId: {}, farmId: {}", userId, firstFarmId);
                return firstFarmId;
            }
        } catch (Exception e) {
            // Redis 연결 실패 시 로깅만 하고 Feign으로 fallback
            log.warn("⚠️ [CACHE] Redis Set 조회 실패, Feign으로 fallback - userId: {}, error: {}", userId, e.getMessage());
        }

        // 2. Redis에 없거나 유효하지 않으면 Feign으로 조회 (fallback)
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
                
                // 3. 모든 farm을 Redis Set에 저장 (다음 요청을 위해)
                try {
                    // 기존 Set 삭제 후 새로 추가 (최신 상태 유지)
                    redisTemplate.delete(setKey);
                    
                    // 모든 farmId를 Set에 추가
                    for (FarmListInfo farm : response.data().content()) {
                        redisTemplate.opsForSet().add(setKey, farm.id().toString());
                        if (firstFarmId == null) {
                            firstFarmId = farm.id(); // 첫 번째 farm 저장
                        }
                    }
                    
                    // Set에 TTL 설정
                    redisTemplate.expire(setKey, TTL);
                    
                    log.debug("💾 [CACHE] All farms saved to Redis Set - userId: {}, farmCount: {}, firstFarmId: {}", 
                        userId, response.data().content().size(), firstFarmId);
                } catch (Exception e) {
                    // Redis 저장 실패해도 계속 진행 (Feign 결과는 반환)
                    log.warn("⚠️ [CACHE] Redis Set 저장 실패 - userId: {}, error: {}", userId, e.getMessage());
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
        String setKey = FARM_SET_KEY_PREFIX + userId;
        try {
            // Set에 farmId 추가
            redisTemplate.opsForSet().add(setKey, farmId.toString());
            redisTemplate.expire(setKey, TTL);
            
            log.info("🔄 [CACHE] Farm cache updated - userId: {}, farmId: {}", userId, farmId);
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Redis 캐시 업데이트 실패 - userId: {}, farmId: {}, error: {}", userId, farmId, e.getMessage());
        }
    }

    /**
     * Farm 삭제 시 Redis 캐시에서 제거
     * 특정 farmId를 Set에서 제거
     *
     * @param userId 사용자 ID (sellerId)
     * @param farmId 삭제할 농장 ID
     */
    public void deleteCache(UUID userId, UUID farmId) {
        String setKey = FARM_SET_KEY_PREFIX + userId;
        try {
            // Set에서 farmId 제거
            redisTemplate.opsForSet().remove(setKey, farmId.toString());
            
            // Set이 비어있으면 전체 삭제
            Long setSize = redisTemplate.opsForSet().size(setKey);
            if (setSize == null || setSize == 0) {
                redisTemplate.delete(setKey);
            }
            
            log.info("🗑️ [CACHE] Farm cache deleted - userId: {}, farmId: {}", userId, farmId);
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Redis 캐시 삭제 실패 - userId: {}, farmId: {}, error: {}", userId, farmId, e.getMessage());
        }
    }

    /**
     * Farm 삭제 시 Redis 캐시 삭제 (하위 호환성)
     * 모든 farm 캐시 삭제
     *
     * @param userId 사용자 ID (sellerId)
     */
    public void deleteCache(UUID userId) {
        String setKey = FARM_SET_KEY_PREFIX + userId;
        try {
            redisTemplate.delete(setKey);
            log.info("🗑️ [CACHE] All farm cache deleted - userId: {}", userId);
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Redis 캐시 삭제 실패 - userId: {}, error: {}", userId, e.getMessage());
        }
    }
}

