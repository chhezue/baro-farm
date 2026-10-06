package com.barofarm.ai.recommend.application.dto;

import com.barofarm.ai.search.domain.ProductDocument;
import java.util.UUID;

/**
 * 개인화 추천, 유사 상품 추천, 레시피 부족 재료 연관 상품 추천에서 공통으로 사용되는 표준 추천 상품 응답 DTO 레코드입니다.
 *
 * [특징]
 * - 프론트엔드 UI 화면에 즉시 렌더링할 수 있도록 상품 식별자, 상품명, 카테고리명, 판매 가격 정보만 간결하게 포함합니다.
 * - 내부 유사도 점수(Cosine Similarity Score)는 UI 노출 대상이 아니므로 제외되었습니다.
 *
 * @param productId           추천 상품의 고유 식별자 (UUID)
 * @param productName         추천 상품의 명칭
 * @param productCategoryName 추천 상품의 카테고리 표시명 (예: "채소", "과일" 등)
 * @param price               상품 판매 가격 (단위: 원)
 */
public record ProductRecommendResponse(
    UUID productId,
    String productName,
    String productCategoryName,
    Long price
) {

    /**
     * Elasticsearch 색인 상품 문서(ProductDocument)를 추천 응답 DTO(ProductRecommendResponse)로 변환합니다.
     *
     * @param product 색인된 상품 문서 엔티티
     * @return 추천 응답 DTO 객체
     */
    public static ProductRecommendResponse from(ProductDocument product) {
        return new ProductRecommendResponse(
            product.getProductId(),
            product.getProductName(),
            product.getProductCategoryName(),
            product.getPrice()
        );
    }
}
