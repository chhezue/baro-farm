package com.barofarm.ai.log.infrastructure.elasticsearch;

import com.barofarm.ai.log.domain.UserBehaviorLogDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 사용자 행동 로그(`user_behavior_logs`) 저장소입니다.
 */
public interface UserBehaviorLogRepository extends ElasticsearchRepository<UserBehaviorLogDocument, String> {

    /**
     * 한 사용자의 최근 행동을 장바구니·주문 구분 없이 최신순으로 읽습니다.
     *
     * 구분 없이 한 번에 읽는 것이 핵심입니다. 유형별로 나눠 읽으면 한쪽에 행동이 몰렸을 때
     * 최근순이 유형 경계에서 깨집니다 — 자세한 배경은 {@link UserBehaviorLogDocument} 참고.
     *
     * @param userId   조회할 사용자
     * @param after    이 시각 이후의 행동만 (예: 30일 전)
     * @param pageable 최대 건수 제한
     */
    List<UserBehaviorLogDocument> findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(
        UUID userId, Instant after, Pageable pageable);
}
