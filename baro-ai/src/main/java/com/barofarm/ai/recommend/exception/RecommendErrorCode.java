package com.barofarm.ai.recommend.exception;

import com.barofarm.ai.common.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecommendErrorCode implements BaseErrorCode {
    // 추천 시스템 관련 에러
    USER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 프로필 벡터를 찾을 수 없습니다."),
    INVALID_TOP_K(HttpStatus.BAD_REQUEST, "추천할 상품 개수는 1 이상이어야 합니다."),
    RECOMMENDATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "추천 생성에 실패했습니다."),
    VECTOR_SEARCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "벡터 유사도 검색에 실패했습니다."),

    // 장바구니 관련 에러
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
