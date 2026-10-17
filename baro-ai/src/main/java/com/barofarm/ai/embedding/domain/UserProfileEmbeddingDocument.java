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
 * Elasticsearch 사용자 취향 프로필 인덱스(`user_profile_embeddings`)에 매핑되는 문서 엔티티입니다.
 *
 * [주요 보관 데이터]
 * - userId: 사용자 고유 식별자 (문서 @Id)
 * - userProfileVector: 사용자의 최근 30일간 행동 로그(장바구니·주문)를 종합하여 산출된 1536차원 L2 정규화 취향 벡터
 * - lastUpdatedAt: 프로필 벡터가 마지막으로 계산·저장된 시각
 * - sourceProductIds: 취향 벡터 산출 시 반영된 기경험(장바구니/주문) 상품 ID 목록 (개인화 추천 시 제외 필터로 사용)
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
     * 상품 임베딩 모델(Gemini)과 동일한 1536차원 규격을 가지며,
     * 개인화 상품 추천 시 상품 벡터들과의 코사인 유사도(Cosine Similarity) 비교 기준으로 활용됩니다.
     */
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private float[] userProfileVector;

    /** 프로필 벡터가 최종 계산되어 갱신된 일시 */
    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;

    /**
     * 취향 벡터 산출에 기여한 기경험(장바구니 담기, 주문 확정) 상품 ID 목록.
     * 개인화 추천 시 사용자가 이미 경험한 상품을 추천 목록에서 중복 노출하지 않기 위한 제외 필터(`mustNot ids`)로 사용됩니다.
     */
    @Field(type = FieldType.Keyword)
    private List<UUID> sourceProductIds;
}
