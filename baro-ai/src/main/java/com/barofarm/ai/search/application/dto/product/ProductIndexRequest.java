package com.barofarm.ai.search.application.dto.product;

import java.time.Instant;
import java.util.UUID;

/**
 * Elasticsearch 상품 검색 및 자동완성 인덱스에 상품 문서를 색인(저장/수정)하기 위한 요청 DTO 레코드입니다.
 *
 * [주요 활용처]
 * - Kafka 상품 이벤트(`ProductEventConsumer`) 수신 시 `ProductIndexService.indexProduct()`로 전달됩니다.
 *
 * @param productId           색인 대상 상품의 고유 UUID
 * @param productName         상품 명칭 (Nori 형태소 분석 및 1536차원 벡터 임베딩 대상)
 * @param productCategoryId   상품 카테고리 고유 UUID
 * @param productCategoryCode 상품 카테고리 코드 (카테고리 필터링 검색용 키워드)
 * @param productCategoryName 상품 카테고리 한글 표시명
 * @param price               상품 판매 가격 (가격 필터링 및 가격 정렬용)
 * @param status              현재 상품 상태 (ON_SALE, DISCOUNTED 등)
 * @param updatedAt           상품 정보 최근 변경 일시 (최신순 정렬 기준)
 */
public record ProductIndexRequest(
    UUID productId,
    String productName,
    UUID productCategoryId,
    String productCategoryCode,
    String productCategoryName,
    Long price,
    String status,
    Instant updatedAt) {}
