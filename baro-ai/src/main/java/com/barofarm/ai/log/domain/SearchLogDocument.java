package com.barofarm.ai.log.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch 'search_logs' 인덱스에 매핑되는 상품 검색 로그 엔티티.
 *
 * 사용자의 검색 질의어와 검색 결과 상위 노출 상품 목록(topProductIds)을 저장하며,
 * 취향 벡터 계산 시 검색어 문자열 대신 상위 상품들의 임베딩 벡터를 활용하는 원천 데이터로 사용됩니다.
 */
@Getter
@Document(indexName = "search_logs")
@NoArgsConstructor
public class SearchLogDocument {

    /** Elasticsearch 문서 식별자 */
    @Id
    private String id;

    /** 검색을 수행한 사용자 ID (비회원 검색 시 null 가능) */
    @Field(type = FieldType.Keyword)
    private UUID userId;

    /** 사용자가 입력한 검색 질의어 (한국어 Nori 형태소 분석기 적용) */
    @Field(type = FieldType.Text, analyzer = "nori")
    private String searchQuery;

    /**
     * 검색 결과 상위에 노출된 상품 ID 목록 (최대 3개).
     * 검색어 텍스트를 실시간 임베딩하는 대신, 이미 벡터가 생성된 상위 상품들의 임베딩을
     * 취향 벡터에 반영하기 위해 저장합니다.
     */
    @Field(type = FieldType.Keyword)
    private List<UUID> topProductIds;

    /** 검색 수행 일시 (취향 벡터 계산의 30일 조회 창·최신순 정렬 기준) */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant searchedAt;

    @Builder
    public SearchLogDocument(UUID userId, String searchQuery, List<UUID> topProductIds, Instant searchedAt) {
        this.userId = userId;
        this.searchQuery = searchQuery;
        this.topProductIds = topProductIds;
        this.searchedAt = searchedAt;
    }
}
