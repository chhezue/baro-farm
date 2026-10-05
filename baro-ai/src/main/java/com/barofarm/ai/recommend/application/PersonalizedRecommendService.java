package com.barofarm.ai.recommend.application;

import com.barofarm.ai.embedding.domain.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.infrastructure.elasticsearch.UserProfileEmbeddingRepository;
import com.barofarm.ai.recommend.application.dto.ProductRecommendResponse;
import com.barofarm.ai.search.application.VectorProductSearchService;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자 취향 기반 개인화 상품 추천 서비스.
 *
 * 사전 계산된 사용자의 1536차원 취향 프로필 벡터를 기반으로 Elasticsearch에서
 * 코사인 유사도 기반 k-NN 상품 후보군을 조회하고, 메도이드(Medoid) 다양성 알고리즘을
 * 적용하여 중복 카테고리 편중이 완화된 맞춤형 추천 상품 목록을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    /** 사용자 프로필 벡터 저장소 레포지토리 */
    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;

    /** 벡터 유사도 기반 Elasticsearch 상품 검색 서비스 */
    private final VectorProductSearchService vectorProductSearchService;

    /** 추천 결과 다양성 확보를 위한 메도이드 군집화 서비스 */
    private final MedoidDiversityService medoidDiversityService;

    /**
     * 특정 사용자에게 맞춤형 개인화 상품 목록을 추천합니다.
     *
     * @param userId 추천 대상 사용자 ID
     * @param topK 최종 추천받을 상품 개수
     * @return 개인화 추천 상품 응답 DTO 목록 (추천 근거 부족 시 빈 목록 반환)
     */
    public List<ProductRecommendResponse> recommendProducts(UUID userId, int topK) {
        // [1단계: 사용자 프로필 벡터 조회 및 유효성 검증]
        // Elasticsearch에서 사용자 ID로 취향 프로필 문서를 조회합니다.
        // 프로필이 아직 생성되지 않았거나 벡터가 없는 경우(행동 로그 부족),
        // 메인 화면 전체 장애를 방지하기 위해 예외를 던지지 않고 빈 목록(List.of())을 즉시 반환합니다.
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null
            || profile.getUserProfileVector() == null
            || profile.getUserProfileVector().isEmpty()) {
            log.warn("사용자 ID {}의 프로필 벡터가 없습니다. 행동 로그가 더 쌓여야 만들어집니다.", userId);
            return List.of();
        }

        // [2단계: 기경험(장바구니/주문) 상품 제외 목록 추출]
        // 사용자가 이미 구매했거나 장바구니에 담아본 상품(sourceProductIds)은 추천 후보에서 제외합니다.
        List<UUID> experiencedProductIds = profile.getSourceProductIds() != null
            ? profile.getSourceProductIds()
            : List.of();

        log.debug("사용자 {}의 추천에서 임베딩의 sourceProductIds({}개)로 상품 제외",
                 userId, experiencedProductIds.size());

        // [3단계: 벡터 유사도 검색 및 메도이드 다양성 필터링 실행]
        // Double 벡터 리스트를 float[] 기본형 배열로 변환한 후 1차 k-NN 검색 및 2차 메도이드 선별을 진행합니다.
        float[] userVector = convertToFloatArray(profile.getUserProfileVector());
        return findSimilarProductsByVector(userVector, topK, experiencedProductIds);
    }

    /**
     * Elasticsearch 벡터 유사도 검색과 메도이드 다양성 필터링을 거쳐 추천 결과를 생성합니다.
     *
     * @param userVector 사용자의 1536차원 취향 벡터
     * @param topK 최종 선별할 추천 상품 수
     * @param excludeProductIds 추천에서 제외할 상품 ID 목록
     * @return 추천 상품 DTO 목록
     */
    private List<ProductRecommendResponse> findSimilarProductsByVector(
        float[] userVector,
        int topK,
        List<UUID> excludeProductIds
    ) {
        // 메도이드 군집화의 다양성을 충분히 확보하기 위해 최종 요청 개수(topK)의 3배(최대 100건) 크기로 1차 후보군을 가져옵니다.
        int candidateSize = Math.min(topK * 3, 100);

        // Elasticsearch k-NN 코사인 유사도 검색을 수행합니다.
        // 사용자 취향 벡터 자체에 선호 카테고리가 이미 반영되어 있으므로 추가 카테고리 보너스는 부여하지 않습니다(null).
        List<ProductDocument> candidates = vectorProductSearchService.findSimilarProductDocumentsByVector(
            userVector,
            candidateSize,
            excludeProductIds,
            null,
            null
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 후보 상품 벡터 간의 거리를 계산하여 동일 카테고리 편중을 완화한 대표 상품(Medoid) topK를 최종 선별합니다.
        List<ProductDocument> medoids =
            medoidDiversityService.selectDiverseMedoids(userVector, candidates, topK);

        // 최종 추천 응답 DTO로 매핑하여 반환합니다.
        return medoids.stream()
            .map(product -> new ProductRecommendResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductCategoryName(),
                product.getPrice()
            ))
            .toList();
    }

    /**
     * List<Double> 타입의 벡터를 Elasticsearch 벡터 검색 클라이언트 요구 타입인 float[] 배열로 변환합니다.
     *
     * @param doubleList Double 타입 벡터 리스트
     * @return float 기본형 배열
     */
    private float[] convertToFloatArray(List<Double> doubleList) {
        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            floatArray[i] = doubleList.get(i).floatValue();
        }
        return floatArray;
    }
}
