package com.barofarm.support.search.domain;

import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Getter
@Document(indexName = "product-autocomplete")
@Setting(settingPath = "settings/autocomplete-analyzer.json")
public class ProductAutocompleteDocument {

    @Id
    private UUID productId;

    @Field(type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    private String productName; // prefix 조각 자동 생성: "토마토" ["토", "토마", "토마토"]

    @Field(type = FieldType.Keyword)
    private String status; // 검색 결과 반환 로직 내에서 ON_SALE, DISCOUNTED 상태만 결과에 노출

    public ProductAutocompleteDocument(UUID productId, String productName, String status) {
        this.productId = productId;
        this.productName = productName;
        this.status = status;
    }
}
