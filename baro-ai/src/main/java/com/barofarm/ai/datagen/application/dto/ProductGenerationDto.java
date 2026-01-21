package com.barofarm.ai.datagen.application.dto;

/**
 * 상품 데이터 전송 객체
 * API 요청(예시)과 응답(생성된 상품) 모두에서 사용
 */
public record ProductGenerationDto(
    String productName,
    String description,
    String productCategory,
    int price,
    Integer stockQuantity,
    String status
) {
}
