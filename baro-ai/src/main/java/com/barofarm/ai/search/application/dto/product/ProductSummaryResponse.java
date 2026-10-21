package com.barofarm.ai.search.application.dto.product;

import com.barofarm.ai.search.domain.ProductDocument;
import java.util.UUID;

/**
 * 목록에 상품 하나를 보여줄 때 쓰는 공통 응답 항목입니다. 화면의 "상품 카드" 한 장에 해당합니다.
 *
 * <p>검색 결과·유사 상품 추천·개인화 추천·레시피의 부족 재료 상품이 **모두 이 타입을 씁니다.**
 * 2026-09-09까지는 `ProductSearchResponse`와 `ProductRecommendResponse`가 필드까지 똑같은 채로
 * 따로 있었고, 레시피 추천은 한쪽을 다른 쪽으로 필드마다 옮겨 담는 반복문을 갖고 있었습니다 —
 * 두 타입이 다른 것을 나타내지 않는데도 변환 코드만 존재했습니다.
 *
 * <p>내부 유사도 점수는 담지 않습니다. 화면에 쓰이지 않고, 노출하면 추천 순서를 점수로 해석하려는
 * 시도를 부르는데 그 점수는 경로마다 의미가 다릅니다(검색은 BM25, 추천은 코사인 유사도).
 *
 * @param productId           상품 고유 식별자
 * @param productName         상품 명칭
 * @param productCategoryName 카테고리 표시명 (예: "채소", "과일")
 * @param price               판매 가격 (원)
 */
public record ProductSummaryResponse(
    UUID productId,
    String productName,
    String productCategoryName,
    Long price
) {

    /**
     * 색인된 상품 문서를 응답 항목으로 바꿉니다. 추천 경로가 Elasticsearch 문서를 직접 받아 쓸 때 씁니다.
     */
    public static ProductSummaryResponse from(ProductDocument product) {
        return new ProductSummaryResponse(
            product.getProductId(),
            product.getProductName(),
            product.getProductCategoryName(),
            product.getPrice()
        );
    }
}
