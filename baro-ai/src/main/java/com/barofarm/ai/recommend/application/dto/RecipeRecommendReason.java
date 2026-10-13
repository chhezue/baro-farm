package com.barofarm.ai.recommend.application.dto;

/**
 * 레시피 추천 응답이 왜 그 모양인지 클라이언트에 알리는 이유 코드입니다.
 *
 * 레시피 추천은 실패를 예외(4xx·5xx)로 올리지 않고 200 + 빈 필드로 내보내는 것이 정책입니다 —
 * 장바구니가 비어 있는 것은 오류가 아니라 사용자 상태이기 때문입니다. 다만 2026-09-06 이전에는
 * 사용자 상태 4종과 장바구니 서비스 장애가 전부 같은 빈 응답이라 화면이 "재료를 담아 보세요"와
 * "잠시 후 다시 시도"를 나눌 수 없었습니다. 이 코드가 그 구분을 담당합니다.
 * 근거: docs/audits/cart-recipe/005-request-path-defects-and-tests.md 판단 항목 1
 */
public enum RecipeRecommendReason {

    /** 레시피를 정상적으로 만들었다. 나머지 필드가 채워져 있다 */
    OK,

    /** 장바구니가 없거나 담긴 상품이 없다 */
    EMPTY_CART,

    /** 상품은 있지만 전부 레시피 제외 카테고리(과일류)라 쓸 재료가 없다 */
    NO_ELIGIBLE_ITEMS,

    /** 상품 문서에 재료명이 없다 — 색인 시점 재료명 추출이 안 됐거나 재색인이 필요하다 */
    INGREDIENTS_NOT_INDEXED,

    /** 보유 재료는 있었지만 LLM이 레시피를 만들지 못했다. `ownedIngredients`는 채워져 있다 */
    GENERATION_FAILED,

    /** 장바구니 서비스 호출 등 외부 연동이 실패했다. 잠시 후 다시 시도할 성격의 실패다 */
    UPSTREAM_ERROR
}
