package com.barofarm.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 제철 판단 기능 관련 설정
 * - 스케줄러 활성화 (@Scheduled 사용)
 * - 비동기 처리 활성화 (@Async 사용)
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SeasonalityConfig {
    // 설정 완료
}


