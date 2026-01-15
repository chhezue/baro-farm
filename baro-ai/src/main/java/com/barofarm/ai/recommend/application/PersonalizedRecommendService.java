package com.barofarm.ai.recommend.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.barofarm.ai.embedding.domain.UserProfileEmbeddingDocument;
import com.barofarm.ai.embedding.infrastructure.elasticsearch.UserProfileEmbeddingRepository;
import com.barofarm.ai.log.domain.CartLogDocument;
import com.barofarm.ai.log.domain.OrderLogDocument;
import com.barofarm.ai.log.domain.SearchLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.CartLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.OrderLogRepository;
import com.barofarm.ai.log.infrastructure.elasticsearch.SearchLogRepository;
import com.barofarm.ai.recommend.application.dto.response.PersonalRecommendResponse;
import com.barofarm.ai.recommend.application.dto.response.PersonalRecommendWithScoreResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendService {

    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchLogRepository searchLogRepository;
    private final CartLogRepository cartLogRepository;
    private final OrderLogRepository orderLogRepository;

    /**
     * 사용자 프로필 벡터를 기반으로 개인화된 상품을 추천합니다.
     *
     * <p>이 메서드는 프로덕션 환경에서 사용하는 일반 추천 메서드입니다.
     * 유사도 점수나 매칭 이유 없이 추천 상품 목록만 반환하므로 성능이 빠릅니다.
     *
     * <p><b>동작 과정:</b>
     * <ol>
     *   <li>사용자 프로필 벡터 조회: user_profile_embeddings 인덱스에서 1536차원 벡터 가져오기</li>
     *   <li>벡터 변환: List<Double>을 float[]로 변환 (Elasticsearch 호환)</li>
     *   <li>벡터 유사도 검색: Elasticsearch script_score 쿼리로 코사인 유사도 계산
     *       <ul>
     *         <li>유사도 점수: cosineSimilarity() + 1.0 (0~2 범위)</li>
     *         <li>상태 필터: ON_SALE, DISCOUNTED 상품만</li>
     *         <li>정렬: 유사도 점수 내림차순</li>
     *       </ul>
     *   </li>
     *   <li>결과 변환: ProductDocument → PersonalRecommendResponse</li>
     * </ol>
     *
     * <p><b>사용 시나리오:</b>
     * <ul>
     *   <li>실제 서비스에서 사용자에게 추천 상품 제공</li>
     *   <li>성능이 중요한 경우 (로그 조회/분석 없음)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param topK 추천할 상품 개수 (예: 15)
     * @return 추천 상품 목록 (productId, productName, productCategory, price만 포함)
     * @throws IllegalArgumentException 벡터가 비어있는 경우
     */
    public List<PersonalRecommendResponse> recommendProducts(UUID userId, int topK) {
        // 1. 사용자 프로필 벡터 조회
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null || profile.getUserProfileVector() == null) {
            log.warn("사용자 ID {}의 프로필 벡터가 없습니다. 임베딩을 먼저 생성해야 합니다.", userId);
            return List.of();
        }

        // 2. 사용자 로그 조회 (이미 주문/장바구니에 담은 상품 제외용)
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Pageable top5 = PageRequest.of(0, 5);
        List<CartLogDocument> cartLogs = cartLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);
        List<OrderLogDocument> orderLogs = orderLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);

        // 3. List<Double>을 float[]로 변환
        float[] userVector = convertToFloatArray(profile.getUserProfileVector());

        // 4. Elasticsearch 벡터 유사도 검색 (이미 주문/장바구니에 담은 상품 제외)
        List<ProductDocument> similarProducts =
            findSimilarProductsByVector(userVector, topK, cartLogs, orderLogs);

        // TODO: [메도이드 알고리즘 적용]
        // 검색 결과가 충분할 때(30개 이상) 메도이드 알고리즘을 적용하여
        // 대표 상품만 선택한 후 변환
        // if (similarProducts.size() >= 30) {
        //     List<ProductDocument> medoidProducts = selectMedoidProducts(similarProducts, topK);
        //     return medoidProducts.stream()...
        // }

        // 4. PersonalRecommendResponse로 변환
        return similarProducts.stream()
            .map(product -> new PersonalRecommendResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductCategory(),
                product.getPrice()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Elasticsearch에서 벡터 유사도 검색을 수행합니다.
     *
     * <p><b>검색 방식:</b>
     * <ul>
     *   <li>script_score 쿼리 사용: Elasticsearch의 script_score 기능으로 벡터 유사도 계산</li>
     *   <li>코사인 유사도: cosineSimilarity(params.query_vector, 'vector') + 1.0
     *       <ul>
     *         <li>원본 코사인 유사도: -1 ~ 1 범위</li>
     *         <li>변환 후 점수: 0 ~ 2 범위 (높을수록 유사)</li>
     *         <li>+1.0을 하는 이유: Elasticsearch 점수가 음수가 되지 않도록 보정</li>
     *       </ul>
     *   </li>
     *   <li>필터링: ON_SALE, DISCOUNTED 상태의 상품만 검색</li>
     *   <li>제외 필터: 이미 주문하거나 장바구니에 담은 상품은 제외</li>
     *   <li>정렬: 유사도 점수 내림차순 (가장 유사한 상품이 먼저)</li>
     * </ul>
     *
     * <p><b>벡터 차원:</b>
     * <ul>
     *   <li>사용자 프로필 벡터: 1536차원 (OpenAI text-embedding-3-small)</li>
     *   <li>상품 벡터: 1536차원 (동일 모델)</li>
     * </ul>
     *
     * <p><b>성능:</b>
     * <ul>
     *   <li>Elasticsearch의 dense_vector 필드 인덱싱 활용</li>
     *   <li>script_score는 모든 상품에 대해 계산하므로 상품 수가 많으면 느려질 수 있음</li>
     *   <li>대안: 향후 knn 검색으로 개선 가능</li>
     * </ul>
     *
     * @param userVector 사용자 프로필 벡터 (1536차원 float 배열)
     * @param topK 반환할 상품 개수
     * @param cartLogs 사용자 장바구니 로그 (제외할 상품 식별용)
     * @param orderLogs 사용자 주문 로그 (제외할 상품 식별용)
     * @return 유사도가 높은 순으로 정렬된 상품 목록 (이미 주문/장바구니에 담은 상품 제외)
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private List<ProductDocument> findSimilarProductsByVector(
        float[] userVector,
        int topK,
        List<CartLogDocument> cartLogs,
        List<OrderLogDocument> orderLogs
    ) {
        try {
            // Elasticsearch script_score 쿼리 생성
            NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                    .scriptScore(ss -> ss
                        .query(q2 -> q2
                            .bool(b -> b
                                .filter(f -> f
                                    .terms(t -> t
                                        .field("status")
                                        .terms(v -> v.value(
                                            List.of(
                                                FieldValue.of("ON_SALE"),
                                                FieldValue.of("DISCOUNTED")
                                            )
                                        ))
                                    )
                                )
                            )
                        )
                        .script(s -> s
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params(java.util.Map.of(
                                "query_vector", co.elastic.clients.json.JsonData.of(convertToDoubleList(userVector))
                            ))
                        )
                    )
                )
                .withPageable(PageRequest.of(0, topK))
                .build();

            // 중복 제거를 위해 더 많은 상품을 가져온 후 필터링 (topK * 2로 충분히 가져옴)
            NativeQuery queryWithBuffer = NativeQuery.builder()
                .withQuery(q -> q
                    .scriptScore(ss -> ss
                        .query(q2 -> q2
                            .bool(b -> b
                                .filter(f -> f
                                    .terms(t -> t
                                        .field("status")
                                        .terms(v -> v.value(
                                            List.of(
                                                FieldValue.of("ON_SALE"),
                                                FieldValue.of("DISCOUNTED")
                                            )
                                        ))
                                    )
                                )
                            )
                        )
                        .script(s -> s
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params(java.util.Map.of(
                                "query_vector", co.elastic.clients.json.JsonData.of(convertToDoubleList(userVector))
                            ))
                        )
                    )
                )
                .withPageable(PageRequest.of(0, topK * 2)) // 중복 제거를 위해 더 많이 가져옴
                .build();

            SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(queryWithBuffer, ProductDocument.class);

            // 이미 주문하거나 장바구니에 담은 상품 ID 수집
            java.util.Set<UUID> excludedProductIds = new java.util.HashSet<>();
            cartLogs.forEach(log -> excludedProductIds.add(log.getProductId()));
            orderLogs.forEach(log -> excludedProductIds.add(log.getProductId()));

            // 중복 제거 및 이미 주문/장바구니에 담은 상품 제외
            java.util.Set<UUID> seenProductIds = new java.util.HashSet<>();
            List<ProductDocument> results = hits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .filter(product -> {
                    UUID productId = product.getProductId();

                    // 중복 제거
                    if (seenProductIds.contains(productId)) {
                        log.debug("중복 상품 제거: productId={}, productName={}",
                            productId, product.getProductName());
                        return false;
                    }

                    // 이미 주문하거나 장바구니에 담은 상품 제외
                    if (excludedProductIds.contains(productId)) {
                        log.debug("이미 주문/장바구니에 담은 상품 제외: productId={}, productName={}",
                            productId, product.getProductName());
                        return false;
                    }

                    seenProductIds.add(productId);
                    return true;
                })
                .limit(topK) // topK 개수만큼만 반환
                .collect(Collectors.toList());

            // TODO: [메도이드 알고리즘 적용]
            // 검색 결과가 많을 때(예: topK가 50개 이상이고 실제 결과가 30개 이상) 메도이드 알고리즘을 적용하여
            // 가장 대표적인 상품들만 선택하는 방식으로 개선 가능
            //
            // 적용 방법:
            // 1. 검색 결과 상품들의 벡터 간 코사인 거리 계산
            // 2. 각 상품이 다른 모든 상품까지의 거리 합 계산
            // 3. 거리 합이 최소인 상품들(메도이드)을 선택하여 최종 추천 리스트 구성
            //
            // 효과적인 데이터 개수:
            // - 최소: 검색 결과 20개 이상에서 효과 시작
            // - 권장: 검색 결과 50개 이상에서 효과적
            // - 특히 유사도가 비슷한 상품들이 많을 때 대표 상품 선택에 유용
            //
            // 예상 효과:
            // - 유사도가 비슷한 상품들 중에서 가장 대표적인 상품만 추천하여 다양성 확보
            // - 추천 리스트의 품질 향상 (중복되거나 비슷한 상품 제거)
            // - 사용자에게 더 의미 있는 추천 제공

            log.debug("사용자 벡터 기반 추천 결과: {}개 상품 발견", results.size());
            return results;

        } catch (Exception e) {
            log.error("벡터 유사도 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 사용자 프로필 벡터를 기반으로 개인화된 상품을 추천합니다. (유사도 점수 및 매칭 이유 포함)
     *
     * <p>이 메서드는 검증 및 디버깅용입니다. 실제 서비스에서는 {@link #recommendProducts(UUID, int)}를 사용하세요.
     *
     * <p><b>추가 기능:</b>
     * <ul>
     *   <li>유사도 점수: 각 추천 상품의 코사인 유사도 점수 (0~2 범위) 포함</li>
     *   <li>매칭 이유 분석: 추천 상품이 사용자 로그와 어떻게 매칭되었는지 분석
     *       <ul>
     *         <li>검색어 매칭: 사용자가 검색한 키워드와 상품명 일치 여부</li>
     *         <li>장바구니 매칭: 장바구니에 담은 상품과 유사한지 확인</li>
     *         <li>주문 매칭: 주문한 상품과 유사한지 확인</li>
     *         <li>카테고리 유사: 직접 매칭은 없지만 카테고리가 유사한 경우</li>
     *         <li>벡터 유사도 기반: 직접 매칭 없이 벡터 유사도만으로 추천된 경우</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <p><b>동작 과정:</b>
     * <ol>
     *   <li>사용자 프로필 벡터 조회</li>
     *   <li>사용자 로그 조회: 최근 30일간의 검색/장바구니/주문 로그 각 최대 5개씩</li>
     *   <li>벡터 유사도 검색 (점수 포함)</li>
     *   <li>매칭 이유 분석: 각 추천 상품이 로그와 어떻게 매칭되었는지 분석</li>
     *   <li>결과 반환: 유사도 점수와 매칭 이유를 포함한 응답</li>
     * </ol>
     *
     * <p><b>성능 고려사항:</b>
     * <ul>
     *   <li>로그 조회 추가: 검색/장바구니/주문 로그 각각 조회 (총 3번의 ES 쿼리)</li>
     *   <li>매칭 분석: 각 추천 상품마다 로그와 비교 (O(n×m) 복잡도)</li>
     *   <li>프로덕션에서는 사용하지 않는 것을 권장</li>
     * </ul>
     *
     * <p><b>사용 시나리오:</b>
     * <ul>
     *   <li>추천 시스템 품질 검증</li>
     *   <li>디버깅: 왜 특정 상품이 추천되었는지 확인</li>
     *   <li>개발/테스트 환경에서 추천 결과 분석</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param topK 추천할 상품 개수
     * @return 추천 상품 목록 (유사도 점수 및 매칭 이유 포함)
     */
    public List<PersonalRecommendWithScoreResponse> recommendProductsWithScore(UUID userId, int topK) {
        // 1. 사용자 프로필 벡터 조회
        UserProfileEmbeddingDocument profile =
            userProfileEmbeddingRepository.findById(userId)
                .orElse(null);

        if (profile == null || profile.getUserProfileVector() == null) {
            log.warn("사용자 ID {}의 프로필 벡터가 없습니다. 임베딩을 먼저 생성해야 합니다.", userId);
            return List.of();
        }

        // 2. 사용자 로그 조회 (매칭 이유 분석용)
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Pageable top5 = PageRequest.of(0, 5);

        List<SearchLogDocument> searchLogs = searchLogRepository
            .findAllByUserIdAndSearchedAtAfterOrderBySearchedAtDesc(userId, thirtyDaysAgo, top5);
        List<CartLogDocument> cartLogs = cartLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);
        List<OrderLogDocument> orderLogs = orderLogRepository
            .findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, thirtyDaysAgo, top5);

        // 3. List<Double>을 float[]로 변환
        float[] userVector = convertToFloatArray(profile.getUserProfileVector());

        // 4. Elasticsearch 벡터 유사도 검색 (점수 포함)
        List<PersonalRecommendWithScoreResponse> results =
            findSimilarProductsByVectorWithScore(userVector, topK, searchLogs, cartLogs, orderLogs);

        return results;
    }

    /**
     * Elasticsearch에서 벡터 유사도 검색을 수행하고 점수 및 매칭 이유를 포함합니다.
     *
     * <p>이 메서드는 {@link #findSimilarProductsByVector(float[], int)}와 동일한 검색 로직을 사용하지만,
     * SearchHit에서 점수를 추출하고 매칭 이유를 분석합니다.
     *
     * <p><b>주요 차이점:</b>
     * <ul>
     *   <li>SearchHit.getScore()로 유사도 점수 추출</li>
     *   <li>각 상품마다 analyzeMatchReason() 호출하여 매칭 이유 분석</li>
     *   <li>PersonalRecommendWithScoreResponse로 변환 (점수 + 매칭 이유 포함)</li>
     * </ul>
     *
     * <p><b>점수 해석:</b>
     * <ul>
     *   <li>1.9 ~ 2.0: 매우 높은 유사도 (사용자 로그와 직접 관련)</li>
     *   <li>1.7 ~ 1.9: 높은 유사도 (사용자 관심사와 매우 유사)</li>
     *   <li>1.5 ~ 1.7: 중간 유사도 (사용자 관심사와 관련)</li>
     *   <li>1.0 ~ 1.5: 낮은 유사도 (약간 관련)</li>
     *   <li>0.0 ~ 1.0: 매우 낮은 유사도 (관련성 낮음)</li>
     * </ul>
     *
     * @param userVector 사용자 프로필 벡터 (1536차원 float 배열)
     * @param topK 반환할 상품 개수
     * @param searchLogs 사용자 검색 로그 (최대 5개)
     * @param cartLogs 사용자 장바구니 로그 (최대 5개)
     * @param orderLogs 사용자 주문 로그 (최대 5개)
     * @return 유사도 점수와 매칭 이유를 포함한 추천 상품 목록
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private List<PersonalRecommendWithScoreResponse> findSimilarProductsByVectorWithScore(
        float[] userVector,
        int topK,
        List<SearchLogDocument> searchLogs,
        List<CartLogDocument> cartLogs,
        List<OrderLogDocument> orderLogs
    ) {
        try {
            NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                    .scriptScore(ss -> ss
                        .query(q2 -> q2
                            .bool(b -> b
                                .filter(f -> f
                                    .terms(t -> t
                                        .field("status")
                                        .terms(v -> v.value(
                                            List.of(
                                                FieldValue.of("ON_SALE"),
                                                FieldValue.of("DISCOUNTED")
                                            )
                                        ))
                                    )
                                )
                            )
                        )
                        .script(s -> s
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params(java.util.Map.of(
                                "query_vector", co.elastic.clients.json.JsonData.of(convertToDoubleList(userVector))
                            ))
                        )
                    )
                )
                .withPageable(PageRequest.of(0, topK))
                .build();

            // 중복 제거를 위해 더 많은 상품을 가져온 후 필터링 (topK * 2로 충분히 가져옴)
            NativeQuery queryWithBuffer = NativeQuery.builder()
                .withQuery(q -> q
                    .scriptScore(ss -> ss
                        .query(q2 -> q2
                            .bool(b -> b
                                .filter(f -> f
                                    .terms(t -> t
                                        .field("status")
                                        .terms(v -> v.value(
                                            List.of(
                                                FieldValue.of("ON_SALE"),
                                                FieldValue.of("DISCOUNTED")
                                            )
                                        ))
                                    )
                                )
                            )
                        )
                        .script(s -> s
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params(java.util.Map.of(
                                "query_vector", co.elastic.clients.json.JsonData.of(convertToDoubleList(userVector))
                            ))
                        )
                    )
                )
                .withPageable(PageRequest.of(0, topK * 2)) // 중복 제거를 위해 더 많이 가져옴
                .build();

            SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(queryWithBuffer, ProductDocument.class);

            // 이미 주문하거나 장바구니에 담은 상품 ID 수집
            java.util.Set<UUID> excludedProductIds = new java.util.HashSet<>();
            cartLogs.forEach(log -> excludedProductIds.add(log.getProductId()));
            orderLogs.forEach(log -> excludedProductIds.add(log.getProductId()));

            List<PersonalRecommendWithScoreResponse> results = new ArrayList<>();
            java.util.Set<UUID> seenProductIds = new java.util.HashSet<>(); // 중복 제거용 Set

            for (SearchHit<ProductDocument> hit : hits.getSearchHits()) {
                ProductDocument product = hit.getContent();
                UUID productId = product.getProductId();

                // 중복 제거: 이미 본 상품은 건너뛰기
                if (seenProductIds.contains(productId)) {
                    log.debug("중복 상품 제거: productId={}, productName={}", productId, product.getProductName());
                    continue;
                }

                // 이미 주문하거나 장바구니에 담은 상품 제외
                if (excludedProductIds.contains(productId)) {
                    log.debug("이미 주문/장바구니에 담은 상품 제외: productId={}, productName={}",
                        productId, product.getProductName());
                    continue;
                }

                // topK 개수만큼만 반환
                if (results.size() >= topK) {
                    break;
                }

                seenProductIds.add(productId);
                double score = hit.getScore(); // 유사도 점수 (0~2 범위)

                // 매칭 이유 분석
                String matchReason = analyzeMatchReason(product, searchLogs, cartLogs, orderLogs);

                results.add(new PersonalRecommendWithScoreResponse(
                    productId,
                    product.getProductName(),
                    product.getProductCategory(),
                    product.getPrice(),
                    score,
                    matchReason
                ));
            }

            log.debug("사용자 벡터 기반 추천 결과 (점수 포함): {}개 상품 발견 (중복 제거 후)", results.size());
            return results;

        } catch (Exception e) {
            log.error("벡터 유사도 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 추천 상품이 사용자 로그와 어떻게 매칭되었는지 분석합니다.
     *
     * <p>이 메서드는 추천된 상품이 사용자의 행동 로그(검색/장바구니/주문)와 어떻게 연관되어 있는지 분석합니다.
     * 추천 시스템의 투명성을 높이고 디버깅에 유용합니다.
     *
     * <p><b>매칭 로직:</b>
     * <ol>
     *   <li><b>검색 로그 매칭:</b>
     *       <ul>
     *         <li>상품명이 검색어를 포함하거나, 검색어가 상품명의 첫 단어를 포함하는 경우</li>
     *         <li>예: 검색어 "사과" → 상품명 "청송사과 프리미엄" ✓</li>
     *         <li>반환 형식: "검색어: {검색어}"</li>
     *       </ul>
     *   </li>
     *   <li><b>장바구니 로그 매칭:</b>
     *       <ul>
     *         <li>상품명이 장바구니 상품명을 포함하거나, 장바구니 상품명이 상품명의 첫 단어를 포함하는 경우</li>
     *         <li>예: 장바구니 "청송사과 프리미엄" → 추천 "청송사과 프리미엄 1kg" ✓</li>
     *         <li>반환 형식: "장바구니: {상품명} ({이벤트타입})"</li>
     *       </ul>
     *   </li>
     *   <li><b>주문 로그 매칭:</b>
     *       <ul>
     *         <li>상품명이 주문 상품명을 포함하거나, 주문 상품명이 상품명의 첫 단어를 포함하는 경우</li>
     *         <li>예: 주문 "제주 감귤" → 추천 "제주 감귤 프리미엄" ✓</li>
     *         <li>반환 형식: "주문: {상품명} ({이벤트타입})"</li>
     *       </ul>
     *   </li>
     *   <li><b>카테고리 매칭:</b>
     *       <ul>
     *         <li>직접 매칭은 없지만 카테고리가 유사한 경우</li>
     *         <li>반환 형식: "카테고리 유사"</li>
     *       </ul>
     *   </li>
     *   <li><b>매칭 없음:</b>
     *       <ul>
     *         <li>모든 로그와 직접 매칭되지 않은 경우</li>
     *         <li>반환 형식: "벡터 유사도 기반 (직접 매칭 없음)"</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p><b>반환값 예시:</b>
     * <ul>
     *   <li>단일 매칭: "검색어: 사과"</li>
     *   <li>복합 매칭: "검색어: 사과, 장바구니: 청송사과 프리미엄 (CART_ITEM_ADDED), 주문: 청송사과 프리미엄 (ORDER_CONFIRMED)"</li>
     *   <li>매칭 없음: "벡터 유사도 기반 (직접 매칭 없음)"</li>
     * </ul>
     *
     * <p><b>주의사항:</b>
     * <ul>
     *   <li>매칭 로직은 단순 문자열 포함 검사이므로, 정확한 의미 매칭은 아님</li>
     *   <li>향후 개선: 임베딩 기반 의미 유사도 매칭으로 확장 가능</li>
     * </ul>
     *
     * @param product 추천된 상품 문서
     * @param searchLogs 사용자 검색 로그 리스트
     * @param cartLogs 사용자 장바구니 로그 리스트
     * @param orderLogs 사용자 주문 로그 리스트
     * @return 매칭 이유 문자열 (여러 개면 쉼표로 구분)
     */
    private String analyzeMatchReason(
        ProductDocument product,
        List<SearchLogDocument> searchLogs,
        List<CartLogDocument> cartLogs,
        List<OrderLogDocument> orderLogs
    ) {
        List<String> reasons = new ArrayList<>();
        String productName = product.getProductName().toLowerCase();
        String productCategory = product.getProductCategory();

        // 1. 검색 로그와 매칭 확인
        for (SearchLogDocument log : searchLogs) {
            String searchQuery = log.getSearchQuery().toLowerCase();
            if (productName.contains(searchQuery) || searchQuery.contains(productName.split(" ")[0])) {
                reasons.add("검색어: " + log.getSearchQuery());
            }
        }

        // 2. 장바구니 로그와 매칭 확인
        for (CartLogDocument log : cartLogs) {
            String logProductName = log.getProductName().toLowerCase();
            if (productName.contains(logProductName) || logProductName.contains(productName.split(" ")[0])) {
                reasons.add("장바구니: " + log.getProductName() + " (" + log.getEventType() + ")");
            }
        }

        // 3. 주문 로그와 매칭 확인
        for (OrderLogDocument log : orderLogs) {
            String logProductName = log.getProductName().toLowerCase();
            if (productName.contains(logProductName) || logProductName.contains(productName.split(" ")[0])) {
                reasons.add("주문: " + log.getProductName() + " (" + log.getEventType() + ")");
            }
        }

        // 4. 카테고리 매칭 확인
        boolean categoryMatch = cartLogs.stream()
            .anyMatch(log -> productCategory != null && productCategory.equals(log.getProductName().split(" ")[0]))
            || orderLogs.stream()
            .anyMatch(log -> productCategory != null && productCategory.equals(log.getProductName().split(" ")[0]));

        if (categoryMatch && reasons.isEmpty()) {
            reasons.add("카테고리 유사");
        }

        if (reasons.isEmpty()) {
            return "벡터 유사도 기반 (직접 매칭 없음)";
        }

        return String.join(", ", reasons);
    }

    /**
     * List<Double>을 float[]로 변환합니다.
     *
     * <p>사용자 프로필 벡터는 Elasticsearch에 List<Double>로 저장되지만,
     * Elasticsearch script_score 쿼리에서는 float[] 형태가 필요합니다.
     *
     * <p><b>변환 이유:</b>
     * <ul>
     *   <li>UserProfileEmbeddingDocument: List<Double> (1536차원)</li>
     *   <li>Elasticsearch script params: float[] 필요</li>
     *   <li>메모리 효율: float가 double보다 메모리 사용량이 적음</li>
     * </ul>
     *
     * <p><b>정밀도:</b>
     * <ul>
     *   <li>Double → float 변환 시 정밀도 손실 가능 (일반적으로 무시 가능한 수준)</li>
     *   <li>임베딩 벡터의 경우 정밀도 손실이 추천 품질에 미치는 영향은 미미함</li>
     * </ul>
     *
     * @param doubleList Double 리스트 (1536차원)
     * @return float 배열 (1536차원)
     * @throws IllegalArgumentException 리스트가 null이거나 비어있는 경우
     */
    private float[] convertToFloatArray(List<Double> doubleList) {
        if (doubleList == null || doubleList.isEmpty()) {
            throw new IllegalArgumentException("벡터 리스트가 비어있습니다.");
        }

        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            floatArray[i] = doubleList.get(i).floatValue();
        }
        return floatArray;
    }

    /**
     * float[]을 List<Double>로 변환합니다.
     *
     * <p>Elasticsearch script_score 쿼리의 params에 벡터를 전달할 때,
     * JsonData.of()가 List<Double> 형태를 요구하므로 변환이 필요합니다.
     *
     * <p><b>사용 위치:</b>
     * <ul>
     *   <li>Elasticsearch script_score 쿼리의 params.query_vector에 전달</li>
     *   <li>예: params.query_vector = [0.123, -0.456, ..., 0.789] (1536개 요소)</li>
     * </ul>
     *
     * <p><b>변환 과정:</b>
     * <ul>
     *   <li>float[] → List<Double>: 각 float 값을 double로 변환</li>
     *   <li>정밀도: float → double 변환은 정밀도 손실 없음 (확장 변환)</li>
     * </ul>
     *
     * @param floatArray float 배열 (1536차원)
     * @return Double 리스트 (1536차원)
     */
    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> doubleList = new java.util.ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
}
