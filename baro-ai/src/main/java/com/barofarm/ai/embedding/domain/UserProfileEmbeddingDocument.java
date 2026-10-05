package com.barofarm.ai.embedding.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch 'user_profile_embeddings' 인덱스에 매핑되는 사용자 취향 프로필 문서 엔티티.
 *
 * 사용자의 최근 행동 로그를 기반으로 산출된 1536차원 취향 임베딩 벡터와,
 * 추천 시 기경험 상품을 배제하기 위한 메타데이터(sourceProductIds)를 보관합니다.
 */
@Getter
@Builder
@Document(indexName = "user_profile_embeddings")
public class UserProfileEmbeddingDocument {

    /** 사용자 식별자 (Elasticsearch 문서의 고유 ID로 사용) */
    @Id
    private UUID userId;

    /**
     * 1536차원 사용자 취향 임베딩 벡터.
     * 상품 임베딩 모델(Gemini)과 동일한 차원 규격을 유지하며,
     * 개인화 추천 시 상품 벡터와의 코사인 유사도 검색 기준으로 활용됩니다.
     */
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private List<Double> userProfileVector;

    /** 프로필 벡터 최종 계산 및 갱신 일시 */
    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;

    /**
     * 취향 벡터 산출 시 반영된 기경험(장바구니 담기, 주문 확정) 상품 ID 목록.
     * 개인화 상품 추천 시 이미 구매했거나 담은 상품을 추천 후보에서 배제하기 위한 필터로 사용됩니다.
     */
    @Field(type = FieldType.Keyword)
    private List<UUID> sourceProductIds;
}
