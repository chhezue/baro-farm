package com.barofarm.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리를 위한 설정 클래스.
 * 사용자 프로필 벡터 갱신을 메인 흐름(장바구니·주문 이벤트 소비)에서 떼어 내
 * 백그라운드에서 실행하는 전용 스레드 풀을 정의합니다. 제출하는 곳은 UserProfileRefreshTrigger 하나뿐입니다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 사용자 취향 프로필 벡터 갱신을 전담하는 ThreadPoolTaskExecutor 빈을 등록합니다.
     *
     * 일반 Executor 대신 구체 클래스 ThreadPoolTaskExecutor를 반환하여
     * Spring Boot Actuator가 큐 대기 수, 활성 스레드 수 등의 메트릭을 자동 수집할 수 있도록 합니다.
     */
    @Bean(name = "profileUpdateExecutor")
    public ThreadPoolTaskExecutor profileUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // [스레드 풀 용량 설정]
        executor.setCorePoolSize(2); // 기본 상주 스레드 수
        executor.setMaxPoolSize(5);  // 부하 증가 시 확장 가능한 최대 스레드 수
        executor.setQueueCapacity(100); // 작업 대기 큐 크기
        executor.setThreadNamePrefix("profile-update-"); // 스레드 추적용 접두사

        // [안전한 종료 정책: Graceful Shutdown]
        // 애플리케이션 종료 시 대기 중인 작업 완료를 최대 60초간 대기합니다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // [대기열 초과 거부 정책: Discard with Warning]
        // 대기 큐(100건)가 가득 찼을 때 기본 예외(RejectedExecutionException)를 던지지 않고 경고 로그만 남긴 후 작업을 스킵합니다.
        // 프로필 벡터는 다음 행동 발생 시 최신 로그로 재계산되므로 일시 누락이 허용되며,
        // Kafka 컨슈머 스레드에 예외를 던져 불필요한 이벤트 재시도 폭풍이 발생하는 것을 방지합니다.
        executor.setRejectedExecutionHandler((task, poolExecutor) ->
            log.warn("프로필 갱신 대기열이 가득 차 이번 갱신을 건너뜁니다. (대기 {}건)",
                poolExecutor.getQueue().size()));

        return executor;
    }
}
