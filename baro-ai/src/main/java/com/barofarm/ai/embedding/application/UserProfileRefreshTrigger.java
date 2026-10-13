package com.barofarm.ai.embedding.application;

import com.barofarm.ai.log.application.LogWriteService;
import java.time.Instant;
import java.util.List;
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
 * 검색은 로그 적재까지 여기서 맡는다 — 검색 응답 스레드에서 Elasticsearch 쓰기를 빼기 위해서다
 * (근거: docs/audits/keyword-search/002-index-throughput-and-search-log.md A-2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileRefreshTrigger {

    /** 프로필 임베딩 계산 및 영속화 서비스 */
    private final UserProfileEmbeddingService userProfileEmbeddingService;

    /** 검색 로그를 Elasticsearch에 적재하는 서비스 (검색 경로에서만 쓴다) */
    private final LogWriteService logWriteService;

    /**
     * 지정된 사용자의 취향 벡터 갱신 작업을 백그라운드 비동기로 실행합니다.
     *
     * @param userId 취향 벡터를 갱신할 대상 사용자 식별자
     */
    // 'profileUpdateExecutor' 스레드 풀에 작업을 위임하여 호출 스레드가 대기 없이 즉시 반환됩니다.
    @Async("profileUpdateExecutor")
    public void refresh(UUID userId) {
        refreshNow(userId);
    }

    /**
     * 검색 로그를 적재한 뒤 같은 스레드에서 취향 벡터를 갱신합니다. 검색 응답 스레드는 이 메서드를 제출만 하고 바로 돌아갑니다.
     *
     * 로그 저장과 갱신을 따로 비동기로 던지지 않고 한 작업으로 묶는 이유는 순서다 — 따로 던지면 갱신이 저장보다
     * 먼저 돌아 이번 검색이 다음 갱신까지 반영되지 않는다. 실행자 큐(100건)가 가득 차면 로그와 갱신이 함께 버려진다.
     * 검색 로그는 취향 분석용 부가 데이터라 이 손실을 허용한다(AsyncConfig의 거부 정책과 같은 판단).
     *
     * @param userId        검색한 사용자 (비회원이면 호출하지 않는다)
     * @param keyword       검색어
     * @param topProductIds 검색 결과 상위 상품 ID (최대 3개) — 취향 벡터는 검색어가 아니라 이 상품들의 벡터를 쓴다
     * @param searchedAt    검색 시각
     */
    @Async("profileUpdateExecutor")
    public void recordSearchAndRefresh(UUID userId, String keyword, List<UUID> topProductIds, Instant searchedAt) {
        try {
            logWriteService.saveSearchLog(userId, keyword, topProductIds, searchedAt);
        } catch (Exception e) {
            // 로그를 못 남겼으면 갱신해도 이번 검색은 반영되지 않는다. 직전 상태로 재계산할 이유가 없어 여기서 끝낸다.
            log.warn("검색 로그 저장 실패 - 사용자: {}, 검색어: {}, 원인: {}", userId, keyword, e.getMessage(), e);
            return;
        }
        refreshNow(userId);
    }

    /**
     * 같은 스레드에서 프로필을 갱신한다. 두 공개 메서드가 공유하는 본문이다 — `refresh`를 내부에서 부르면 자기 호출이라
     * 프록시를 타지 않으므로 본문을 여기 두고 둘 다 이걸 부른다.
     * 프로필 갱신 실패가 메인 비즈니스 흐름(Kafka 이벤트 소비 완료, 검색 응답 등)에 영향을 주지 않도록
     * 예외를 안에서 격리하고 경고 로그만 남긴다.
     */
    private void refreshNow(UUID userId) {
        try {
            log.debug("프로필 벡터 갱신 시작 - 사용자: {}", userId);
            userProfileEmbeddingService.updateUserProfileEmbedding(userId);
        } catch (Exception e) {
            log.warn("프로필 벡터 갱신 실패 - 사용자: {}, 원인: {}", userId, e.getMessage());
        }
    }
}
