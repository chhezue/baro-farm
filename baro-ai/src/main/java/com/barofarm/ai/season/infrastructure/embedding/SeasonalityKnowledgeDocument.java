package com.barofarm.ai.season.infrastructure.embedding;

import java.util.Map;
import org.springframework.ai.document.Document;

/**
 * 제철 지식 문서 (VectorStore 저장용)
 *
 * CSV 데이터를 임베딩하여 VectorStore에 저장할 때 사용
 */
public class SeasonalityKnowledgeDocument {

    /**
     * CSV 데이터로부터 Document 생성
     *
     * @param productName 상품명
     * @param category 카테고리
     * @param content 제철 설명 (CSV의 content 필드)
     * @param seasonalityType 제철 타입
     * @param seasonalityValue 제철 값
     * @param sourceType 데이터 소스 타입
     * @return VectorStore에 저장할 Document
     */
    public static Document createDocument(
            String productName,
            String category,
            String content,
            String seasonalityType,
            String seasonalityValue,
            String sourceType) {

        // 검색 가능한 텍스트 생성 (임베딩 대상)
        String documentText = String.format(
            "%s(%s): %s 제철: %s",
            productName, category, content, seasonalityValue
        );

        // 메타데이터 (검색 시 필터링 또는 추가 정보로 사용)
        Map<String, Object> metadata = Map.of(
            "productName", productName,
            "category", category,
            "seasonalityType", seasonalityType,
            "seasonalityValue", seasonalityValue,
            "sourceType", sourceType,
            "id", generateId(productName, category)
        );

        return new Document(documentText, metadata);
    }

    /**
     * 문서 ID 생성 (productName + category 기반)
     */
    private static String generateId(String productName, String category) {
        return String.format("seasonality:%s:%s", category.toLowerCase(), productName);
    }
}
