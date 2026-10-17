package com.barofarm.ai.search.infrastructure.elasticsearch;

import com.barofarm.ai.search.domain.ProductAutocompleteDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 실시간 검색어 자동완성 전용 Elasticsearch 인덱스(`product-autocomplete`)에 접근하는 레포지토리 인터페이스입니다.
 *
 * [색인 및 쿼리 동작 원리]
 * - 색인: `ProductAutocompleteDocument`의 `productName` 필드는 `edge_ngram` 분석기를 사용하여
 *   "토마토" 색인 시 ["토", "토마", "토마토"] 접두어 토큰들이 역색인 사전에 미리 저장됩니다.
 * - 조회: 따라서 검색 시 복잡한 정규식이나 wildcard 쿼리 대신 단순 `match` 쿼리로 접두어 토큰을 O(1) 수준으로 매우 빠르게 탐색할 수 있습니다.
 * - 상태 필터: 현재 구매 가능한 `ON_SALE` 및 `DISCOUNTED` 상태의 상품만 자동완성 후보로 노출합니다.
 *   같은 목록을 검색·추천은 `ProductDocument.PURCHASABLE_STATUSES`에서 읽습니다. 여기만 값을 문자열로
 *   적어 두는데, `@Query`는 어노테이션이라 상수를 끼워 넣을 수 없기 때문입니다 — 상태를 늘리거나 줄일 때는
 *   그 상수와 아래 `status` 목록을 **같이** 고쳐야 합니다.
 */
public interface ProductAutocompleteRepository
    extends ElasticsearchRepository<ProductAutocompleteDocument, UUID> {

    /**
     * 입력된 접두어(prefix)와 일치하는 상품명 및 상태를 가진 자동완성 문서를 조회합니다.
     *
     * @param prefix   사용자가 입력 중인 검색어 접두어 (예: "토마")
     * @param pageable 페이징 및 최대 결과 개수(size) 제어 객체
     * @return 일치하는 자동완성 상품 문서 목록
     */
    @Query("""
        {
          "bool": {
            "must": {
              "match": {
                "productName": {
                  "query": "?0",
                  "operator": "and"
                }
              }
            },
            "filter": {
              "terms": {
                "status": ["ON_SALE", "DISCOUNTED"]
              }
            }
          }
        }
        """)
    List<ProductAutocompleteDocument> findByPrefix(String prefix, Pageable pageable);
}
