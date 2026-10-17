package com.barofarm.ai.search.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.json.JsonData;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSearchResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductAutocompleteRepository;
import com.barofarm.dto.CustomPage;
import com.barofarm.exception.CustomException;
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
 * 조회만 하고 아무것도 기록하지 않습니다. 2026-09-07까지는 회원 검색마다 검색 로그를 남기고 취향 벡터
 * 갱신을 요청했으나, 검색을 취향 신호에서 빼면서 그 경로를 통째로 지웠다 — 검색 결과 상위에 뜬 상품을
 * 그 사람의 취향으로 삼는 방식이었기 때문이다.
 * 근거: docs/audits/taste-vector/005-signal-and-weight-simplification.md
 * 상품명 접두어 자동완성 조회도 같은 조회 경로라 여기에 둡니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

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

    /** 상품명 접두어 자동완성 Elasticsearch 레포지토리 */
    private final ProductAutocompleteRepository autocompleteRepository;

    /**
     * 키워드 및 다중 필터 조건에 부합하는 상품 목록을 페이징 조회합니다.
     *
     * @param request 검색 요청 파라미터 (키워드, 카테고리 목록, 가격 범위)
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 상품 수 (1~100)
     * @return 페이징 처리된 상품 검색 결과 응답
     */
    public CustomPage<ProductSearchResponse> search(
        ProductSearchRequest request,
        int page,
        int size
    ) {
        Pageable pageable = toPageable(page, size);

        // [1단계: Elasticsearch NativeQuery 복합 쿼리 빌드]
        NativeQuery query = buildSearchQuery(request, pageable);

        // [2단계: Elasticsearch 쿼리 실행]
        SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);

        // [3단계: 검색 결과 DTO 변환 및 CustomPage 페이징 포장]
        List<ProductSearchResponse> items = toResponses(hits);

        return CustomPage.of(hits.getTotalHits(), items, pageable);
    }

    /**
     * 부족 재료 상품 조회처럼 키워드 여러 개를 한 번에 찾을 때 씁니다. 키워드마다 `search`를 따로 부르면
     * ES 왕복이 키워드 수만큼 순차로 늘어나므로(2026-09-06 감사 cart-recipe/005 A-2), 같은 쿼리를
     * 키워드 수만큼 만들어 `_msearch` 한 번으로 보냅니다. 필터·정렬·`_source` 제외는 `search`와 같습니다.
     * 검색 로그·프로필 갱신은 하지 않습니다 — 사용자의 검색이 아니라 서버 내부 조회이기 때문입니다.
     *
     * 배치 안의 쿼리 하나가 실패하면 Spring Data ES는 그 항목을 결과에서 빼고 자리를 당겨 버린다(어느 것이 실패했는지는
     * 알 수 없다). 그러면 호출자가 순서로 맞추는 대응이 어긋나므로, 결과 수가 키워드 수와 다르면 전부 빈 목록으로 돌려준다 —
     * 상품 카드 하나를 엉뚱한 재료에 붙이는 것보다 카드가 빠지는 쪽이 낫다.
     *
     * @param keywords       찾을 키워드 목록 (빈 목록이면 빈 결과)
     * @param sizePerKeyword 키워드당 최대 상품 수 (1~100)
     * @return 요청한 키워드 순서와 1:1로 대응하는 상품 목록의 목록 (길이는 항상 키워드 수와 같다)
     */
    public List<List<ProductSearchResponse>> searchByKeywords(List<String> keywords, int sizePerKeyword) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Pageable pageable = toPageable(0, sizePerKeyword);

        List<NativeQuery> queries = keywords.stream()
            .map(keyword -> buildIngredientQuery(keyword, pageable))
            .toList();

        List<SearchHits<ProductDocument>> hitsPerKeyword = operations.multiSearch(queries, ProductDocument.class);
        if (hitsPerKeyword.size() != keywords.size()) {
            log.warn("multi-search 결과 수({})가 키워드 수({})와 달라 순서를 믿을 수 없어 전부 빈 결과로 돌립니다. keywords={}",
                hitsPerKeyword.size(), keywords.size(), keywords);
            return keywords.stream().map(k -> List.<ProductSearchResponse>of()).toList();
        }

        return hitsPerKeyword.stream()
            .map(this::toResponses)
            .toList();
    }

    /**
     * 상품 ID 목록으로 상품 문서를 한 번에 읽습니다. 레시피 추천이 장바구니 상품의 재료 표준명을 읽을 때 씁니다.
     *
     * 벡터는 받지 않습니다 — 부르는 쪽이 쓰지 않는데 문서당 약 21KB가 붙기 때문입니다. 상품 인덱스에 질의하는
     * 코드를 이 클래스 하나로 모으려고 여기 둡니다. 예전에는 레시피 추천이 ElasticsearchOperations를 직접
     * 주입받아 같은 질의를 따로 조립했고, 그래서 제외 필드 목록이 두 곳에 복제돼 있었습니다.
     * 근거: docs/audits/cross-cutting/003-stale-comments-and-write-only-fields.md B-2
     *
     * @param productIds 읽을 상품 ID 목록 (빈 목록이면 빈 결과)
     * @return 찾은 상품 문서 목록. 색인되지 않은 ID는 결과에서 빠지므로 요청보다 적을 수 있습니다
     */
    public List<ProductDocument> findByIds(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q.ids(i -> i.values(productIds.stream().map(UUID::toString).toList())))
            .withSourceFilter(FetchSourceFilter.of(null, EXCLUDED_SOURCE_FIELDS))
            .withPageable(PageRequest.of(0, productIds.size()))
            .build();

        return operations.search(query, ProductDocument.class).getSearchHits().stream()
            .map(hit -> hit.getContent())
            .toList();
    }

    /**
     * 검색 히트를 응답 DTO 목록으로 변환합니다.
     */
    private List<ProductSearchResponse> toResponses(SearchHits<ProductDocument> hits) {
        return hits.getSearchHits().stream()
            .map(h -> h.getContent())
            .map(d -> new ProductSearchResponse(
                d.getProductId(),
                d.getProductName(),
                d.getProductCategoryName(),
                d.getPrice()
            ))
            .toList();
    }

    /**
     * 부족 재료 하나를 상품에 잇는 전용 쿼리를 만듭니다. 상품명 매칭 세 가지는 일반 검색과 같고,
     * 여기에 **색인 시점에 뽑아 둔 재료 표준명(`ingredientName`) 정확 일치**를 가장 높은 가중치로 더합니다.
     *
     * 이 절이 없으면 상품명이 재료를 다른 말로 부르는 상품을 영영 못 찾는다 — `한우 등심 300g`은
     * `ingredientName`이 `소고기`인데 상품명 어디에도 `소고기`가 없다. 상품 20건 표본에서 18건 중 1건이
     * 그랬고, 나머지가 맞은 것은 상품명에 재료 단어가 우연히 들어 있어서지 설계가 아니었다.
     * 근거: docs/audits/cart-recipe/006-missing-ingredient-product-match.md A-1
     *
     * `term`에는 정규화하지 않은 원문을 넣습니다. 색인값이 LLM 원문을 `trim()`만 한 것이고 그 필드에
     * `normalizer`가 없어서, 비교용 정규화(소문자화·공백 제거)를 태우면 오히려 어긋납니다. 표기가 흔들리는
     * 경우는 색인 시점 프롬프트 준수에 맡깁니다 — ES 쪽 정규화는 매핑 변경과 전체 재색인을 부르는데,
     * 이 저장소에 아직 재색인 경로가 없습니다.
     *
     * 일반 검색(`search`)은 이 절을 쓰지 않습니다. 사용자가 "소고기"를 검색했을 때 한우 등심이 나와야
     * 하는지는 별개 판단이라 이번 변경 범위 밖입니다.
     */
    private NativeQuery buildIngredientQuery(String ingredientName, Pageable pageable) {
        return NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    if (ingredientName != null && !ingredientName.isBlank()) {
                        // 재료 표준명 정확 일치 — 상품명 매칭(2.0)보다 높게 둬 정답이 1위로 오게 한다
                        b.should(sh -> sh.term(t -> t
                            .field("ingredientName")
                            .value(ingredientName)
                            .boost(4.0f)));
                        applyKeywordShouldClauses(b, ingredientName);
                        b.minimumShouldMatch("1");
                    }
                    applyStatusFilter(b);
                    return b;
                }))
                .withSourceFilter(FetchSourceFilter.of(null, EXCLUDED_SOURCE_FIELDS))
                .withPageable(pageable)
                .build();
    }

    /**
     * 상품명 기반 매칭 세 가지(정확 문구 2.0 · Nori 형태소 1.0 · 오탈자 0.3)를 should 절에 등록합니다.
     * 일반 검색과 부족 재료 조회가 이 부분을 공유합니다.
     */
    private void applyKeywordShouldClauses(BoolQuery.Builder b, String keyword) {
        applyExactMatch(b, keyword);
        applyNormalMatch(b, keyword);
        if (keyword.length() >= MIN_FUZZY_KEYWORD_LENGTH) {
            applyFuzzyMatch(b, keyword);
        }
    }

    /**
     * 키워드 조건(should: 정확 문구, 일반 형태소, 오탈자 보정)과 상태/카테고리/가격 필터(filter)를 조합한
     * 상품 검색 쿼리를 만듭니다.
     */
    private NativeQuery buildSearchQuery(ProductSearchRequest request, Pageable pageable) {
        return NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {

                    // 키워드가 입력된 경우 3중 가중치 매칭 조건을 should 절에 등록
                    if (request.keyword() != null && !request.keyword().isBlank()) {
                        applyKeywordShouldClauses(b, request.keyword());

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
                     ProductDocument.PURCHASABLE_STATUSES.stream().map(FieldValue::of).toList()
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
