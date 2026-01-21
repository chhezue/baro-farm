package com.barofarm.ai.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리를 위한 설정
 * 사용자 프로필 벡터 업데이트 등 백그라운드 작업에 사용됩니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 사용자 프로필 업데이트를 위한 비동기 실행자
     */
    @Bean(name = "profileUpdateExecutor")
    public Executor profileUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // 기본 스레드 수
        executor.setMaxPoolSize(5); // 최대 스레드 수
        executor.setQueueCapacity(100); // 큐 용량
        executor.setThreadNamePrefix("profile-update-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
