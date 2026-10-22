package com.barofarm.ai.recommend.application.dto;

import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import java.util.List;

/**
 * 레시피 추천 시 장바구니에 없는 '부족한 식재료'에 대해 판매 중인 상품을 함께 추천하는 응답 DTO 레코드입니다.
 *
 * @param ingredientName 부족한 식재료 명칭 (예: "돼지고기", "양파")
 * @param products       해당 식재료로 Elasticsearch에서 검색된 실제 판매 상품 추천 목록 (최대 2건)
 */
public record IngredientRecommendResponse(
    String ingredientName,
    List<ProductSummaryResponse> products
) { }
