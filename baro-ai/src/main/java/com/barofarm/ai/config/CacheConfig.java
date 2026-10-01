package com.barofarm.ai.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정.
 * Spring Boot 기본 폴백(ConcurrentMapCacheManager)은 TTL·최대 크기 제한이 없어
 * 무제한으로 쌓이므로 쓰지 않고, Caffeine으로 명시적으로 크기·TTL을 제한한다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // 자동완성 캐시: 입력할 때마다 호출되고 흔한 접두어는 여러 사용자가 반복 요청하므로
    // 캐시 효과가 크다. 상품 상태가 바뀌면(가격·품절 등) 최대 5분간 지연 반영될 수 있으나
    // 자동완성은 후보 노출용이라 허용 가능한 수준으로 판단했다.
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("autocomplete");
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
        );
        return cacheManager;
    }
}
