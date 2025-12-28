package com.barofarm.support.experience.infrastructure.cache;

import com.barofarm.support.common.client.FarmClient;
import feign.FeignException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private static final Duration TTL = Duration.ofDays(1); // Farm 정보는 자주 변경되지 않음

    /**
     * 사용자 ID로 농장 ID 조회 (하이브리드 방식)
     * 1. Redis에서 조회 (빠름)
     * 2. 없으면 Feign으로 조회 (fallback)
     * 3. 조회 결과를 Redis에 저장 (다음 요청을 위해)
     *
     * @param userId 사용자 ID
     * @return 농장 ID 또는 null
     */
    public UUID getFarmIdByUserId(UUID userId) {
        String key = FARM_KEY_PREFIX + userId;

        try {
            // 1. Redis에서 조회
            String farmIdStr = redisTemplate.opsForValue().get(key);
            if (farmIdStr != null) {
                log.debug("✅ [CACHE] Farm ID found in Redis - userId: {}, farmId: {}", userId, farmIdStr);
                return UUID.fromString(farmIdStr);
            }
        } catch (Exception e) {
            // Redis 연결 실패 시 로깅만 하고 Feign으로 fallback
            log.warn("⚠️ [CACHE] Redis 조회 실패, Feign으로 fallback - userId: {}, error: {}", userId, e.getMessage());
        }

        // 2. Redis에 없으면 Feign으로 조회 (fallback)
        try {
            UUID farmId = farmClient.getFarmIdByUserId(userId);

            // 3. 조회 결과를 Redis에 저장 (다음 요청을 위해)
            if (farmId != null) {
                try {
                    redisTemplate.opsForValue().set(key, farmId.toString(), TTL);
                    log.debug("💾 [CACHE] Farm ID saved to Redis - userId: {}, farmId: {}", userId, farmId);
                } catch (Exception e) {
                    // Redis 저장 실패해도 계속 진행 (Feign 결과는 반환)
                    log.warn("⚠️ [CACHE] Redis 저장 실패 - userId: {}, farmId: {}, error: {}", userId, farmId, e.getMessage());
                }
            }

            return farmId;
        } catch (FeignException e) {
            if (e.status() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Farm 이벤트 수신 시 Redis 캐시 업데이트
     *
     * @param userId 사용자 ID (sellerId)
     * @param farmId 농장 ID
     */
    public void updateCache(UUID userId, UUID farmId) {
        String key = FARM_KEY_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(key, farmId.toString(), TTL);
            log.info("🔄 [CACHE] Farm cache updated - userId: {}, farmId: {}", userId, farmId);
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Redis 캐시 업데이트 실패 - userId: {}, farmId: {}, error: {}", userId, farmId, e.getMessage());
        }
    }

    /**
     * Farm 삭제 시 Redis 캐시 삭제
     *
     * @param userId 사용자 ID (sellerId)
     */
    public void deleteCache(UUID userId) {
        String key = FARM_KEY_PREFIX + userId;
        try {
            redisTemplate.delete(key);
            log.info("🗑️ [CACHE] Farm cache deleted - userId: {}", userId);
        } catch (Exception e) {
            log.warn("⚠️ [CACHE] Redis 캐시 삭제 실패 - userId: {}, error: {}", userId, e.getMessage());
        }
    }
}

