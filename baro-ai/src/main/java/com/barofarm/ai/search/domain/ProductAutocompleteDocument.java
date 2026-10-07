package com.barofarm.ai.search.domain;

import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Elasticsearch 실시간 자동완성 전용 인덱스(`product-autocomplete`)에 매핑되는 엔티티입니다.
 *
 * [인덱스 분석기 설정]
 * - `autocomplete-analyzer.json`: 한글 Edge N-gram 토큰 필터를 적용하여
 *   "토마토" 색인 시 ["토", "토마", "토마토"]와 같은 접두어 토큰을 사전 생성하여 빠른 접두어 검색을 지원합니다.
 */
@Getter
@Document(indexName = "product-autocomplete")
@Setting(settingPath = "settings/autocomplete-analyzer.json")
public class ProductAutocompleteDocument {

    /** 상품 고유 UUID */
    @Id
    private UUID productId;

    /**
     * Edge N-gram 분석기가 적용된 상품명 필드.
     * 색인 시에는 `autocomplete_analyzer`로 접두어 토큰이 생성되고,
     * 조회 시에는 `standard` 분석기를 통해 입력어가 그대로 매칭됩니다.
     */
    @Field(type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    private String productName;

    /**
     * 상품 판매 상태 (ON_SALE, DISCOUNTED 상태만 자동완성 결과로 노출).
     */
    @Field(type = FieldType.Keyword)
    private String status;

    public ProductAutocompleteDocument(UUID productId, String productName, String status) {
        this.productId = productId;
        this.productName = productName;
        this.status = status;
    }
}
