package com.barofarm.ai.log.infrastructure.elasticsearch;

import com.barofarm.ai.log.domain.SearchLogDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 상품 검색 로그('search_logs') Elasticsearch 저장소 인터페이스.
 */
public interface SearchLogRepository extends ElasticsearchRepository<SearchLogDocument, String> {

    /**
     * 특정 사용자의 기준 일시 이후에 발생한 검색 로그를 검색 시각 내림차순으로 페이징 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @param after 조회 시작 기준 시각 (예: 현재 시점 기준 30일 전)
     * @param pageable 페이징 조건 (최대 조회 건수 제한)
     * @return 검색 시각 내림차순으로 정렬된 검색 로그 목록
     */
    List<SearchLogDocument> findAllByUserIdAndSearchedAtAfterOrderBySearchedAtDesc(
        UUID userId, Instant after, Pageable pageable);
}
