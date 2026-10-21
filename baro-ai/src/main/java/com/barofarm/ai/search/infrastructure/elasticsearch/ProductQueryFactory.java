package com.barofarm.ai.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.json.JsonData;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.domain.ProductDocument;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

/**
 * 상품 인덱스에 보낼 Elasticsearch 질의를 조립합니다.
 *
 * <p>2026-09-09에 {@code ProductSearchService}에서 떼어 냈습니다. 그 클래스가 426줄까지 자란 이유의
 * 절반이 이 조립 코드였고, 성격도 달랐습니다 — 유스케이스를 조율하는 코드와 Elasticsearch 질의 문법을
 * 다루는 코드가 한 파일에 섞여 있었습니다. 검색어 가중치나 필터 규칙을 고칠 때 봐야 할 곳이 여기 하나로 모입니다.
 *
 * <p>상태가 없어 전부 정적 메서드입니다. 인스턴스를 만들 이유가 없으므로 생성자를 막아 둡니다.
 */
public final class ProductQueryFactory {

    /** 오탈자 매칭을 적용할 최소 검색어 길이 */
    private static final int MIN_FUZZY_KEYWORD_LENGTH = 2;

    /** 응답에 쓰지 않는 dense_vector를 Elasticsearch에서 전송받지 않도록 제외하는 필드 (문서당 약 10KB를 아낀다) */
    static final String[] EXCLUDED_SOURCE_FIELDS = {"vector"};

    /** 자동완성 응답이 실제로 쓰는 필드. 나머지는 받지 않는다 */
    static final String[] AUTOCOMPLETE_SOURCE_FIELDS = {"productId", "productName"};

    private ProductQueryFactory() {
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
    public static NativeQuery buildIngredientQuery(String ingredientName, Pageable pageable) {
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
    static void applyKeywordShouldClauses(BoolQuery.Builder b, String keyword) {
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
    public static NativeQuery buildSearchQuery(ProductSearchRequest request, Pageable pageable) {
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
                // 응답에 쓰지 않는 벡터(문서당 약 10KB)를 받아 오지 않는다.
                .withSourceFilter(FetchSourceFilter.of(null, EXCLUDED_SOURCE_FIELDS))
                .withPageable(pageable)
                .build();
    }

    /**
     * 정확한 구문 일치 조건 추가 (MatchPhraseQuery).
     * 단어 간 최대 1개 단어 차이(slop 1)를 허용하며, 가장 높은 가중치(boost 2.0)를 부여합니다.
     */
    static void applyExactMatch(BoolQuery.Builder b, String keyword) {
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
    static void applyNormalMatch(BoolQuery.Builder b, String keyword) {
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
    static void applyFuzzyMatch(BoolQuery.Builder b, String keyword) {
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
    static void applyStatusFilter(BoolQuery.Builder b) {
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
    static void applyCategoryFilter(BoolQuery.Builder b, List<String> categoryCodes) {
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
    static void applyPriceFilter(BoolQuery.Builder b, Long priceMin, Long priceMax) {
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

    /**
     * 상품명 접두어로 자동완성 후보를 찾는 질의입니다.
     *
     * `operator=and`인 이유는 입력한 토큰이 전부 들어간 상품명만 후보로 두기 위해서입니다.
     * 색인 쪽 `edge_ngram`이 접두어 조각을 이미 갖고 있어 조회에서 다시 확장하지 않습니다.
     */
    public static NativeQuery autocomplete(String prefix, Pageable pageable) {
        return NativeQuery.builder()
            .withQuery(q -> q.bool(b -> {
                b.must(m -> m.match(mm -> mm
                    .field("productName.ac")
                    .query(prefix)
                    .operator(Operator.And)));
                applyStatusFilter(b);
                return b;
            }))
            .withSourceFilter(FetchSourceFilter.of(AUTOCOMPLETE_SOURCE_FIELDS, null))
            .withPageable(pageable)
            .build();
    }

    /**
     * 상품 ID 목록으로 문서를 그대로 읽는 질의입니다. 벡터는 받지 않습니다.
     */
    public static NativeQuery byIds(List<String> productIds, Pageable pageable) {
        return NativeQuery.builder()
            .withQuery(q -> q.ids(i -> i.values(productIds)))
            .withSourceFilter(FetchSourceFilter.of(null, EXCLUDED_SOURCE_FIELDS))
            .withPageable(pageable)
            .build();
    }
}
