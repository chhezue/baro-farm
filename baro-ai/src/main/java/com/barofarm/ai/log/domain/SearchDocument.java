package com.barofarm.ai.log.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Document(indexName = "search_logs")
@NoArgsConstructor
public class SearchDocument {

    @Id
    private String id; // Elasticsearch에서는 String ID 사용

    // 개인화 추천의 핵심 축: 누가 (userId)
    @Field(type = FieldType.Keyword)
    private UUID userId;

    // user embedding을 만들 핵심 텍스트 데이터 - 검색 가능하도록 Text 타입
    @Field(type = FieldType.Text, analyzer = "nori")
    private String searchQuery;

    // 검색 카테고리 필터 (보조 데이터)
    @Field(type = FieldType.Keyword)
    private String category;

    // 검색 시각 (시간 패턴 분석용)
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant searchedAt;

    @Builder
    public SearchDocument(UUID userId, String searchQuery, String category, Instant searchedAt) {
        this.userId = userId;
        this.searchQuery = searchQuery;
        this.category = category;
        this.searchedAt = searchedAt;
    }
}
