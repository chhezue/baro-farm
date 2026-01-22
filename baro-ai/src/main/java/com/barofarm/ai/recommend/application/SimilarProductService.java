package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.search.application.VectorProductSearchService;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 상품 디테일 페이지에서 비슷한 상품 반환 (벡터 유사도)
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarProductService {

    private final ProductSearchRepository productSearchRepository;
    private final VectorProductSearchService vectorProductSearchService;

    public List<ProductRecommendResponse> recommendSimilarProducts(UUID productId, int topK) {
        // 1. 기준 상품 조회 및 벡터 추출
        ProductDocument product = productSearchRepository.findById(productId)
            .orElse(null);

        // 상품을 찾을 수 없는 경우 빈 리스트 반환 (에러를 던지지 않음)
        // MSA 환경에서 상품 디테일 페이지의 다른 부분이 정상 작동하도록 함
        if (product == null) {
            log.warn("유사 상품 추천을 위한 기준 상품을 찾을 수 없습니다. productId: {} (빈 리스트 반환)", productId);
            return List.of();
        }

        if (product.getVector() == null) {
            log.warn("기준 상품 '{}'의 벡터가 존재하지 않습니다. 임베딩이 필요합니다.", product.getProductName());
            return List.of();
        }

        float[] productVector = product.getVector();

        // 2. 벡터 유사도 검색 실행
        return findSimilarProducts(productVector, productId, topK);
    }

    // 특정 벡터와 유사한 상품들을 Elasticsearch에서 검색
    private List<ProductRecommendResponse> findSimilarProducts(float[] vector, UUID originalProductId, int topK) {
        // VectorProductSearchService의 메소드 사용
        return vectorProductSearchService.findSimilarProductsByVector(
            vector,
            topK,
            List.of(),          // 제외할 상품 ID 없음
            originalProductId,  // 자기 자신 제외
            false               // 중복 제거 비활성화
        );
    }
}
