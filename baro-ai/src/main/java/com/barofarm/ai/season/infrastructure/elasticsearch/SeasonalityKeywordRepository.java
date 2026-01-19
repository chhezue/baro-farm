package com.barofarm.ai.season.infrastructure.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 제철 지식 키워드 검색용 Elasticsearch Repository
 */
public interface SeasonalityKeywordRepository extends ElasticsearchRepository<SeasonalityKeywordDocument, String> {
    
    /**
     * productName과 category로 문서 존재 여부 확인
     */
    boolean existsByProductNameAndCategory(String productName, String category);
}

