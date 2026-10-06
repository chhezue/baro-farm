package com.barofarm.ai.search.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.json.JsonData;
import com.barofarm.ai.embedding.application.UserProfileRefreshTrigger;
import com.barofarm.ai.log.application.LogWriteService;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSearchResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.dto.CustomPage;
import com.barofarm.exception.CustomException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

/**
 * 상품 키워드 검색·자동완성 조회 서비스.
 *
 * Elasticsearch를 활용하여 키워드 유사도 매칭(정확 구문, 형태소 분절, 오탈자 보정)과
 * 다중 필터(판매 상태, 카테고리, 가격 범위)를 조합한 복합 상품 검색을 수행하며,
 * 검색 성공 시 사용자 취향 분석을 위한 검색 로그 기록 및 프로필 벡터 갱신을 비동기로 연계합니다.
 * 상품명 접두어 자동완성 조회도 같은 조회 경로라 여기에 둡니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    /** 취향 분석용 검색 로그에 보존할 상위 검색 결과 상품 최대 개수 (3건) */
    private static final int SEARCH_LOG_TOP_PRODUCT_LIMIT = 3;

    /** 오탈자 매칭을 적용할 최소 검색어 길이 */
    private static final int MIN_FUZZY_KEYWORD_LENGTH = 2;

    /** 한 페이지에 담을 수 있는 최대 상품 수 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 자동완성 후보 최대 개수 */
    private static final int MAX_AUTOCOMPLETE_SIZE = 20;

    /** 자동완성을 시작할 최소 입력 길이 */
    private static final int MIN_AUTOCOMPLETE_QUERY_LENGTH = 2;

    /** Elasticsearch가 한 번에 훑을 수 있는 최대 결과 위치 (index.max_result_window 기본값) */
    private static final int MAX_RESULT_WINDOW = 10_000;

    /** 응답 DTO 변환 시 사용하지 않는 1536차원 dense_vector를 Elasticsearch에서 전송받지 않도록 제외하는 필드 목록 (네트워크 대역폭 및 메모리 절감) */
    private static final String[] EXCLUDED_SOURCE_FIELDS = {"vector"};

    /** Spring Data Elasticsearch 네이티브 쿼리 실행 템플릿 */
    private final ElasticsearchOperations operations;

    /** 사용자 행동 로그 영속화 서비스 */
    private final LogWriteService logWriteService;

    /** 사용자 프로필 벡터 비동기 갱신 트리거 컴포넌트 */
    private final UserProfileRefreshTrigger userProfileRefreshTrigger;

    /** 상품명 접두어 자동완성 Elasticsearch 레포지토리 */
    private final ProductAutocompleteRepository autocompleteRepository;

    /**
     * 키워드 및 다중 필터 조건에 부합하는 상품 목록을 페이징 조회합니다.
     *
     * @param userId 검색을 수행한 사용자 ID (비로그인 시 null)
     * @param request 검색 요청 파라미터 (키워드, 카테고리 목록, 가격 범위)
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 상품 수 (1~100)
     * @return 페이징 처리된 상품 검색 결과 응답
     */
    public CustomPage<ProductSearchResponse> search(
        UUID userId,
        ProductSearchRequest request,
        int page,
        int size
    ) {
        Pageable pageable = toPageable(page, size);

        // [1단계: Elasticsearch NativeQuery 복합 쿼리 빌드]
        // 키워드 조건(should: 정확 문구, 일반 형태소, 오탈자 보정)과 상태/카테고리/가격 필터(filter)를 조합합니다.
        NativeQuery query =
            NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {

                    // 키워드가 입력된 경우 3중 가중치 매칭 조건을 should 절에 등록
                    if (request.keyword() != null && !request.keyword().isBlank()) {
                        String keyword = request.keyword();

                        // 1) 정확 문구 일치 (가장 높은 가중치: boost 2.0)
                        applyExactMatch(b, keyword);
                        // 2) Nori 형태소 일치 (기본 가중치: boost 1.0)
                        applyNormalMatch(b, keyword);

                        // 3) 2글자 이상인 경우 오탈자 교정 매칭 허용 (가장 낮은 가중치: boost 0.3)
                        if (keyword.length() >= MIN_FUZZY_KEYWORD_LENGTH) {
                            applyFuzzyMatch(b, keyword);
                        }

                        // should 절에 등록된 조건 중 최소 1개 이상 만족하는 상품만 검색 대상에 포함
                        b.minimumShouldMatch("1");
                    }
                    // 판매 상태 필터 (ON_SALE, DISCOUNTED)
                    applyStatusFilter(b);
                    // 카테고리 다중 선택 필터
                    applyCategoryFilter(b, request.categoryCodes());
                    // 가격 범위 필터 (priceMin ~ priceMax)
                    applyPriceFilter(b, request.priceMin(), request.priceMax());

                    return b;
                }))
                // 정렬 조건: 1순위 검색 관련도 점수(_score 내림차순), 2순위 최신 등록/수정일(updatedAt 내림차순)
                .withSort(s -> s.score(sc -> sc.order(SortOrder.Desc)))
                .withSort(s -> s.field(f -> f.field("updatedAt").order(SortOrder.Desc)))
                // 응답에 쓰지 않는 벡터(문서당 약 21KB)를 받아 오지 않는다.
                .withSourceFilter(FetchSourceFilter.of(null, EXCLUDED_SOURCE_FIELDS))
                .withPageable(pageable)
                .build();

        // [2단계: Elasticsearch 쿼리 실행]
        SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);

        // [3단계: 검색 결과 DTO 변환 및 CustomPage 페이징 포장]
        List<ProductSearchResponse> items =
            hits.getSearchHits().stream()
                .map(h -> h.getContent())
                .map(d -> new ProductSearchResponse(
                    d.getProductId(),
                    d.getProductName(),
                    d.getProductCategoryName(),
                    d.getPrice()
                ))
                .toList();

        CustomPage<ProductSearchResponse> resultPage =
            CustomPage.of(hits.getTotalHits(), items, pageable);

        // [4단계: 비동기 부가 작업 (검색 로그 적재 및 취향 벡터 갱신 트리거)]
        // 회원 검색이면서 검색어가 존재하는 경우, 상위 검색 결과 상품 ID(최대 3개)를 포함하여 로그를 남기고 취향 벡터를 비동기 갱신합니다.
        // 로그 저장 또는 갱신 실패가 메인 검색 응답에 영향을 주지 않도록 try-catch로 예외를 격리합니다.
        if (userId != null && request.keyword() != null && !request.keyword().isBlank()) {
            try {
                // 취향 벡터 산출 시 검색어 대신 활용할 상위 노출 상품 ID(최대 3개) 추출
                List<UUID> topProductIds = items.stream()
                    .map(ProductSearchResponse::productId)
                    .limit(SEARCH_LOG_TOP_PRODUCT_LIMIT)
                    .toList();

                logWriteService.saveSearchLog(
                    userId,
                    request.keyword(),
                    topProductIds,
                    Instant.now()
                );
                userProfileRefreshTrigger.refresh(userId);
            } catch (Exception e) {
                log.warn("❌ 검색 로그 저장 실패 - 사용자: {}, 검색어: {}, 원인: {}",
                    userId, request.keyword(), e.getMessage(), e);
            }
        }

        return resultPage;
    }

    /**
     * 상품명 접두어 기반 자동완성 검색을 수행합니다.
     *
     * @param query 사용자가 입력 중인 검색어 접두어
     * @param size 반환할 최대 자동완성 추천 결과 수 (1~20)
     * @return 자동완성 상품 응답 DTO 목록
     */
    // 동일한 접두어 질의에 대해 Caffeine 로컬 인메모리 캐시를 적용하여 ES 조회 부하를 절감합니다.
    @Cacheable(value = "autocomplete", key = "#query + '-' + #size")
    public List<ProductAutoCompleteResponse> autocomplete(String query, int size) {
        if (size < 1 || size > MAX_AUTOCOMPLETE_SIZE) {
            throw new CustomException(SearchErrorCode.INVALID_AUTOCOMPLETE_SIZE);
        }

        // 2글자 미만 입력 시 불필요한 검색을 차단하고 빈 목록을 즉시 반환합니다.
        if (query == null || query.length() < MIN_AUTOCOMPLETE_QUERY_LENGTH) {
            return List.of();
        }

        return autocompleteRepository.findByPrefix(query, PageRequest.of(0, size)).stream()
            .map(document -> new ProductAutoCompleteResponse(document.getProductId(), document.getProductName()))
            .toList();
    }

    /**
     * 페이징 파라미터를 검증하고 Pageable로 변환합니다.
     * 범위를 벗어나면 ES가 400을 돌려주거나(from + size 초과) 응답이 무제한으로 커지므로 진입부에서 끊습니다.
     */
    private Pageable toPageable(int page, int size) {
        if (page < 0) {
            throw new CustomException(SearchErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(SearchErrorCode.INVALID_PAGE_SIZE);
        }
        if ((long) (page + 1) * size > MAX_RESULT_WINDOW) {
            throw new CustomException(SearchErrorCode.PAGE_RANGE_EXCEEDED);
        }
        return PageRequest.of(page, size);
    }

    /**
     * 정확한 구문 일치 조건 추가 (MatchPhraseQuery).
     * 단어 간 최대 1개 단어 차이(slop 1)를 허용하며, 가장 높은 가중치(boost 2.0)를 부여합니다.
     */
    private void applyExactMatch(BoolQuery.Builder b, String keyword) {
        b.should(m ->
            m.matchPhrase(mp ->
                mp.field("productName")
                  .query(keyword)
                  .slop(1)
                  .boost(2.0f)
            )
        );
    }

    /**
     * 형태소 분절 키워드 일치 조건 추가 (MatchQuery).
     * Nori 형태소 분석기를 거쳐 분절된 토큰 중 하나라도 일치하면 검색되며, 기본 가중치(boost 1.0)를 가집니다.
     */
    private void applyNormalMatch(BoolQuery.Builder b, String keyword) {
        b.should(m ->
            m.match(mm ->
                mm.field("productName")
                  .query(keyword)
                  .operator(Operator.Or)
                  .boost(1.0f)
            )
        );
    }

    /**
     * 오탈자 교정 키워드 일치 조건 추가.
     *
     * 띄어쓰기 단위로 토큰화한 서브필드(productName.word)를 대상으로 단어마다 한 글자까지 오타를 허용한다.
     * 상품명 전체를 한 덩어리로 저장한 필드에 걸면 검색어와 길이 차가 커져 매칭 자체가 불가능했다.
     * 첫 1글자는 일치해야 하며(prefixLength 1), 가장 낮은 가중치(boost 0.3)를 부여한다.
     */
    private void applyFuzzyMatch(BoolQuery.Builder b, String keyword) {
        b.should(m ->
            m.match(mm ->
                mm.field("productName.word")
                  .query(keyword)
                  .fuzziness("1")
                  .prefixLength(1)
                  .boost(0.3f)
            )
        );
    }

    /**
     * 상품 판매 상태 필터 추가.
     * 판매 중(ON_SALE) 또는 할인 중(DISCOUNTED) 상태의 상품만 검색 대상에 포함합니다.
     */
    private void applyStatusFilter(BoolQuery.Builder b) {
        b.filter(f ->
            f.terms(t ->
                t.field("status")
                 .terms(v -> v.value(
                     List.of(
                         FieldValue.of("ON_SALE"),
                         FieldValue.of("DISCOUNTED")
                     )
                 ))
            )
        );
    }

    /**
     * 카테고리 다중 선택 필터 추가.
     * 지정된 카테고리 코드 목록 중 하나와 일치하는 상품만 필터링합니다.
     */
    private void applyCategoryFilter(BoolQuery.Builder b, List<String> categoryCodes) {
        if (categoryCodes == null || categoryCodes.isEmpty()) {
            return;
        }

        b.filter(f ->
            f.terms(t ->
                t.field("productCategoryCode")
                    .terms(v ->
                        v.value(
                            categoryCodes.stream()
                                .map(FieldValue::of)
                                .toList()
                        )
                    )
            )
        );
    }

    /**
     * 가격 범위 필터 추가.
     * 최소 가격(priceMin) 이상, 최대 가격(priceMax) 이하인 상품만 필터링합니다.
     */
    private void applyPriceFilter(BoolQuery.Builder b, Long priceMin, Long priceMax) {
        if (priceMin == null && priceMax == null) {
            return;
        }

        b.filter(f ->
            f.range(r -> {
                var range = r.field("price");
                if (priceMin != null) {
                    range = range.gte(JsonData.of(priceMin));
                }
                if (priceMax != null) {
                    range = range.lte(JsonData.of(priceMax));
                }
                return range;
            })
        );
    }
}
