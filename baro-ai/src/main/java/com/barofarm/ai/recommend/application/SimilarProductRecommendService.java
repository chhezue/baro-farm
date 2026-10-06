package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 상품 상세 페이지에서 현재 조회 중인 상품과 가장 유사한 상품들을 추천하는 애플리케이션 서비스입니다.
 *
 * [주요 동작 원리]
 * 1. 기준 상품 문서(ProductDocument)를 Elasticsearch에서 조회하여 사전 계산된 1536차원 임베딩 벡터를 추출합니다.
 * 2. 기준 상품 본인은 추천 목록에 다시 나오지 않도록 제외 목록(excludeProductIds)에 포함합니다.
 * 3. 공통 추천 파이프라인(VectorRecommendPipeline)에 위임하여 kNN 코사인 유사도 검색과 Max-Min 다양성 선별을 거친 추천 상품 목록을 반환합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarProductRecommendService {

    /** Elasticsearch에 색인된 상품 문서 및 임베딩 벡터를 조회하는 레포지토리 */
    private final ProductSearchRepository productSearchRepository;

    /** 기준 벡터를 받아 kNN 검색 및 다양성 선별을 수행하는 공통 추천 파이프라인 */
    private final VectorRecommendPipeline vectorRecommendPipeline;

    /**
     * 기준 상품과 유사한 추천 상품 topK 목록을 반환합니다.
     *
     * @param productId 기준이 되는 상품의 고유 UUID
     * @param topK      추천받을 상품 최대 개수
     * @return 유사 상품 추천 응답 DTO 목록 (기준 상품 미존재 또는 벡터 미생성 시 빈 리스트)
     */
    public List<ProductRecommendResponse> recommendSimilarProducts(UUID productId, int topK) {
        // [1단계: 기준 상품 문서 조회 및 유효성 검증]
        // Elasticsearch에서 상품 ID로 색인 문서를 조회합니다.
        // 상품이 존재하지 않는 경우 상세 페이지 전체 렌더링 장애를 방지하기 위해 예외를 던지지 않고 빈 리스트를 반환합니다.
        ProductDocument product = productSearchRepository.findById(productId)
            .orElse(null);

        if (product == null) {
            log.warn("유사 상품 추천을 위한 기준 상품을 찾을 수 없습니다. productId: {} (빈 리스트 반환)", productId);
            return List.of();
        }

        // [2단계: 기준 상품의 임베딩 벡터 존재 여부 검증]
        // 비동기 색인 작업 지연 등으로 임베딩 벡터가 아직 생성되지 않은 경우 조기 종료합니다.
        if (product.getVector() == null || product.getVector().length == 0) {
            log.warn("기준 상품 '{}'의 벡터가 존재하지 않습니다. 임베딩이 필요합니다. productId: {}",
                product.getProductName(), productId);
            return List.of();
        }

        // [3단계: 공통 추천 파이프라인 위임 (자기 자신 제외 및 다양성 선별)]
        // 기준 상품의 벡터를 쿼리 벡터로 전달하고, 자기 자신(productId)은 추천 결과에서 제외하도록 지정합니다.
        return vectorRecommendPipeline.recommend(product.getVector(), List.of(productId), topK);
    }
}
