package com.barofarm.ai.embedding.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 사용자 프로필 벡터 비동기 갱신 트리거 컴포넌트.
 *
 * Spring의 {@link Async} AOP 프록시 동작을 보장하기 위해 별도의 빈으로 분리되어 있으며,
 * 장바구니/주문 이벤트 컨슈머 및 상품 검색 서비스에서 취향 벡터 갱신이 필요할 때 공통 진입점으로 사용됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileRefreshTrigger {

    /** 프로필 임베딩 계산 및 영속화 서비스 */
    private final UserProfileEmbeddingService userProfileEmbeddingService;

    /**
     * 지정된 사용자의 취향 벡터 갱신 작업을 백그라운드 비동기로 실행합니다.
     *
     * @param userId 취향 벡터를 갱신할 대상 사용자 식별자
     */
    // 'profileUpdateExecutor' 스레드 풀에 작업을 위임하여 호출 스레드가 대기 없이 즉시 반환됩니다.
    @Async("profileUpdateExecutor")
    public void refresh(UUID userId) {
        // 프로필 갱신 실패가 메인 비즈니스 흐름(Kafka 이벤트 소비 완료, 검색 응답 등)에 영향을 주지 않도록
        // 예외를 내부에서 안전하게 격리하고 경고 로그만 남깁니다.
        try {
            log.debug("프로필 벡터 갱신 시작 - 사용자: {}", userId);
            userProfileEmbeddingService.updateUserProfileEmbedding(userId);
        } catch (Exception e) {
            log.warn("프로필 벡터 갱신 실패 - 사용자: {}, 원인: {}", userId, e.getMessage());
        }
    }
}
