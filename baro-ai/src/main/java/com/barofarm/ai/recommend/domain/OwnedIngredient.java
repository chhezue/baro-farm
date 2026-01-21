package com.barofarm.ai.recommend.domain;

// 사용자가 보유한 재료 정보 도메인
public record OwnedIngredient(
    String name,               // 재료 이름
    String sourceProductName,  // 재료가 나온 상품 이름
    String sourceCategoryName  // 재료가 나온 상품 카테고리
) {

}
