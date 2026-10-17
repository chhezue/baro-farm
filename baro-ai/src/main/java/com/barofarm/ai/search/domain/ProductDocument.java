package com.barofarm.ai.search.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

/**
 * Elasticsearch 메인 상품 검색 인덱스(`product`)에 매핑되는 상품 도메인 엔티티입니다.
 *
 * [인덱스 매핑 및 검색 필드 구성]
 * - productId: 상품 고유 식별자 (문서 ID)
 * - productName: Nori 형태소 분석(기본 검색) 및 Standard 분석(오탈자 단어 매칭용 subfield) 다중 필드 적용
 * - productCategoryId / Code / Name: 상품 카테고리 필터링 및 응답 노출용 키워드 필드
 * - price: 가격 범위 검색 및 정렬용 Long 필드
 * - status: 현재 판매 상태 (ON_SALE, DISCOUNTED 상태만 검색 및 추천에 노출)
 * - updatedAt: 최신순 정렬(2순위)에 쓰는 이벤트 발행 시각 — 상품이 바뀐 시각이 아니다
 * - ingredientName: 레시피 추천을 위해 상품 색인 시점에 사전 추출된 요리 재료 표준명
 * - vector: Google Gemini 임베딩 모델로 생성된 1536차원 dense_vector (`script_score` 코사인 유사도 검색용)
 */
@Getter
@Document(indexName = "product")
public class ProductDocument {

    /**
     * 검색·추천·자동완성이 결과에 내보내는 판매 상태. 지금 실제로 살 수 있는 상품만 고른다는 뜻이다.
     *
     * 이 목록을 늘리거나 줄이면 세 경로가 함께 움직여야 한다 — 키워드 검색, 벡터 유사도 후보 조회,
     * 그리고 자동완성이다. 자동완성만 이 상수를 쓰지 못한다(레포지토리 @Query 문자열이라 값을 끼워 넣을 수
     * 없다) — ProductAutocompleteRepository.findByPrefix의 status 목록을 같이 고쳐야 한다.
     * 근거: docs/audits/cross-cutting/003-stale-comments-and-write-only-fields.md B-3
     */
    public static final List<String> PURCHASABLE_STATUSES = List.of("ON_SALE", "DISCOUNTED");

    /** 상품 고유 식별자 (Elasticsearch 문서의 @Id) */
    @Id
    private UUID productId;

    /**
     * 상품명 다중 필드 (MultiField).
     * - mainField: 한국어 Nori 형태소 분석기를 적용하여 한글 검색어 형태소 일치(`match`, `match_phrase`) 검색에 사용됩니다.
     * - word (subfield): Standard 분석기를 적용하여 띄어쓰기 단위로 토큰화하며, 오탈자 보정(`fuzzy`) 검색 시 단어별 1글자 교정에 사용됩니다.
     */
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "nori"),
        otherFields = {
            @InnerField(
                suffix = "word",
                type = FieldType.Text,
                analyzer = "standard"
            )
        }
    )
    private String productName;

    /** 상품 카테고리 UUID */
    @Field(type = FieldType.Keyword)
    private UUID productCategoryId;

    /** 상품 카테고리 코드 (레시피 추천 제외 카테고리 필터링 등에 사용) */
    @Field(type = FieldType.Keyword)
    private String productCategoryCode;

    /** 상품 카테고리 한글 표시명 */
    @Field(type = FieldType.Keyword)
    private String productCategoryName;

    /** 상품 판매 가격 (가격 범위 필터 및 가격순 정렬에 사용) */
    @Field(type = FieldType.Long)
    private Long price;

    /** 상품 판매 상태 (ON_SALE, DISCOUNTED, SOLD_OUT 등) */
    @Field(type = FieldType.Keyword)
    private String status;

    /** baro-shopping이 상품 이벤트를 발행한 시각. 검색 결과 최신순 정렬(2순위)의 기준이다. */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    /** 색인 시점에 LLM으로 정규화하여 저장한 요리 재료 표준명 (레시피 추천 시 즉시 조회됨) */
    @Field(type = FieldType.Keyword)
    private String ingredientName;

    /** Google Gemini 임베딩 모델로 생성된 1536차원 상품 임베딩 벡터. 유사 상품·개인화 추천의 코사인 유사도 계산에 쓴다. */
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private float[] vector;

    public ProductDocument() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public ProductDocument(
        UUID productId,
        String productName,
        UUID productCategoryId,
        String productCategoryCode,
        String productCategoryName,
        Long price,
        String status,
        Instant updatedAt,
        String ingredientName,
        float[] vector) {
        this.productId = productId;
        this.productName = productName;
        this.productCategoryId = productCategoryId;
        this.productCategoryCode = productCategoryCode;
        this.productCategoryName = productCategoryName;
        this.price = price;
        this.status = status;
        this.updatedAt = updatedAt;
        this.ingredientName = ingredientName;
        this.vector = vector;
    }
}
