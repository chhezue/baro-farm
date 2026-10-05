package com.barofarm.ai.log.infrastructure.elasticsearch;

import com.barofarm.ai.log.domain.CartLogDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 장바구니 행동 로그('cart_event_logs') Elasticsearch 저장소 인터페이스.
 */
public interface CartLogRepository extends ElasticsearchRepository<CartLogDocument, String> {

    /**
     * 특정 사용자의 기준 일시 이후에 발생한 장바구니 로그를 발생 시각 내림차순으로 페이징 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @param after 조회 시작 기준 시각 (예: 현재 시점 기준 30일 전)
     * @param pageable 페이징 조건 (최대 조회 건수 제한)
     * @return 발생 시각 내림차순으로 정렬된 장바구니 로그 목록
     */
    List<CartLogDocument> findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(
        UUID userId, Instant after, Pageable pageable);
}
