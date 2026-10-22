package com.barofarm.ai.search.infrastructure.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * `baro-shopping`의 상품 목록 응답 중 색인에 필요한 필드만 담습니다.
 *
 * <p>원본은 `baro-shopping`의 `product.application.dto.ProductDetailInfo`이며 판매자·설명·이미지·재고 옵션·
 * 리뷰 요약도 함께 보냅니다. 색인은 검색·추천에 쓰는 값만 필요해서 옮겨 적지 않았습니다 — Jackson이 모르는
 * 필드를 무시하도록 돼 있어 수신에 지장이 없고, 쓰지도 않는 필드를 옮겨 적으면 상류가 바뀔 때 이쪽까지
 * 같이 고쳐야 하는 것처럼 보이기 때문입니다.
 *
 * @param productStatus 상류의 상품 상태 이름. 색인 문서의 `status`가 되고, 구매 가능 여부 판정에 쓰인다
 * @param updatedAt     상품이 마지막으로 바뀐 시각. 검색 결과 최신순 정렬의 2순위 기준이 된다.
 *                      이벤트 경로와 달리 여기서는 발행 시각이 아니라 실제 변경 시각이라 정렬이 더 정확하다
 */
public record CatalogProduct(
    UUID id,
    String productName,
    UUID categoryId,
    String categoryCode,
    String categoryName,
    Long price,
    String productStatus,
    LocalDateTime updatedAt
) { }
