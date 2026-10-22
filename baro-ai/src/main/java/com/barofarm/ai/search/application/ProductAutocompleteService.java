package com.barofarm.ai.search.application;

import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductQueryFactory;
import com.barofarm.exception.CustomException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

/**
 * 검색창에 입력하는 동안 상품명 후보를 돌려주는 서비스입니다.
 *
 * <p>키워드 검색과 분리해 둔 이유는 규칙이 다르기 때문입니다 — 입력이 2글자는 돼야 시작하고, 결과 개수 상한이
 * 따로 있으며, 같은 접두어가 반복해서 들어오므로 캐시를 씁니다. 2026-09-09까지는 {@code ProductSearchService}
 * 안에 함께 있었습니다.
 *
 * <p>2026-09-08부터는 별도 인덱스가 아니라 상품 문서의 `productName.ac` 서브필드를 봅니다 — 같은 상품명을
 * 두 인덱스에 나눠 쓰면 한쪽만 성공하는 경우를 막을 방법이 없었기 때문입니다
 * ({@link ProductDocument#getProductName()} 참고). 색인·조회 방식은 그대로라 결과는 달라지지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAutocompleteService {

    /** 자동완성 후보 최대 개수 */
    private static final int MAX_AUTOCOMPLETE_SIZE = 20;

    /** 자동완성을 시작할 최소 입력 길이 */
    private static final int MIN_AUTOCOMPLETE_QUERY_LENGTH = 2;

    /** Spring Data Elasticsearch 네이티브 쿼리 실행 템플릿 */
    private final ElasticsearchOperations operations;

    /**
     * 입력 중인 접두어로 상품명 후보를 찾습니다.
     *
     * 흔한 접두어는 여러 사용자가 반복해서 보내므로 결과를 인스턴스 안에 잠시 기억합니다(크기·유지 시간은
     * {@code CacheConfig}). 상품 상태가 바뀌면 그 시간만큼 지연 반영될 수 있으나, 자동완성은 후보를
     * 보여 주는 용도라 허용 가능한 수준으로 판단했습니다 — 근거: docs/decisions/001-autocomplete-caching.md
     *
     * @param query 사용자가 입력 중인 검색어 접두어 (2글자 미만이면 빈 목록)
     * @param size 반환할 최대 개수 (1~20)
     */
    @Cacheable(value = "autocomplete", key = "#query + '-' + #size")
    public List<ProductAutoCompleteResponse> autocomplete(String query, int size) {
        if (size < 1 || size > MAX_AUTOCOMPLETE_SIZE) {
            throw new CustomException(SearchErrorCode.INVALID_AUTOCOMPLETE_SIZE);
        }

        // 2글자 미만 입력은 후보가 너무 넓어 의미가 없다 — ES를 부르지 않고 즉시 빈 목록이다.
        if (query == null || query.length() < MIN_AUTOCOMPLETE_QUERY_LENGTH) {
            return List.of();
        }

        return operations.search(
                ProductQueryFactory.autocomplete(query, PageRequest.of(0, size)), ProductDocument.class)
            .getSearchHits().stream()
            .map(hit -> hit.getContent())
            .map(document -> new ProductAutoCompleteResponse(document.getProductId(), document.getProductName()))
            .toList();
    }
}
