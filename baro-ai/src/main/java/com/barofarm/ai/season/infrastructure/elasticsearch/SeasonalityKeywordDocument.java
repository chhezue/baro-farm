package com.barofarm.ai.season.infrastructure.elasticsearch;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 제철 지식 키워드 검색용 Elasticsearch Document
 * 
 * Fuzzy matching을 위한 키워드 검색에 사용
 */
@Getter
@Document(indexName = "seasonality-keyword", createIndex = true)
public class SeasonalityKeywordDocument {
    
    @Id
    private String id; // "seasonality:{category}:{productName}" 형식
    
    @Field(type = FieldType.Text, analyzer = "nori")
    private String productName; // 키워드 검색 대상 (Fuzzy matching 가능)
    
    @Field(type = FieldType.Keyword)
    private String category;
    
    @Field(type = FieldType.Keyword)
    private String seasonalityType;
    
    @Field(type = FieldType.Keyword)
    private String seasonalityValue;
    
    @Field(type = FieldType.Keyword)
    private String sourceType; // MANUAL, LLM_GENERATED
    
    @Field(type = FieldType.Double)
    private Double confidence; // 신뢰도 (LLM 생성 데이터의 경우)
    
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ssX", timezone = "UTC")
    private Instant createdAt;
    
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ssX", timezone = "UTC")
    private Instant updatedAt;
    
    public SeasonalityKeywordDocument() {
    }
    
    public SeasonalityKeywordDocument(
            String id,
            String productName,
            String category,
            String seasonalityType,
            String seasonalityValue,
            String sourceType,
            Double confidence,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.productName = productName;
        this.category = category;
        this.seasonalityType = seasonalityType;
        this.seasonalityValue = seasonalityValue;
        this.sourceType = sourceType;
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * 문서 ID 생성
     */
    public static String generateId(String productName, String category) {
        return String.format("seasonality:%s:%s", category.toLowerCase(), productName);
    }
}

