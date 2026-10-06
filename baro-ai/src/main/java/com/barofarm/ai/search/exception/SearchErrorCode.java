package com.barofarm.ai.search.exception;

import com.barofarm.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 상품 검색 및 자동완성 처리 과정에서 발생할 수 있는 비즈니스 예외 코드 열거형(Enum)입니다.
 *
 * [예외 분류]
 * - 페이징 유효성 예외: 페이지 번호 음수, 페이지 크기 허용치(1~100) 초과, ES max_result_window(10,000) 초과
 * - 자동완성 파라미터 예외: 자동완성 결과 요청 개수(1~20) 초과
 */
@Getter
@AllArgsConstructor
public enum SearchErrorCode implements BaseErrorCode {

    /** 페이지 번호가 0 미만(음수)으로 전달된 경우 */
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다."),

    /** 한 페이지 상품 크기(size)가 1 미만이거나 100을 초과한 경우 */
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 1 이상 100 이하여야 합니다."),

    /** (page + 1) * size가 Elasticsearch 기본 최대 조회 범위(10,000)를 초과하여 ES 오류가 발생할 수 있는 경우 */
    PAGE_RANGE_EXCEEDED(HttpStatus.BAD_REQUEST, "조회 가능한 범위를 넘었습니다. 검색 조건을 좁혀 주세요."),

    /** 자동완성 요청 개수(size)가 1 미만이거나 20을 초과한 경우 */
    INVALID_AUTOCOMPLETE_SIZE(HttpStatus.BAD_REQUEST, "자동완성 개수는 1 이상 20 이하여야 합니다.");

    /** HTTP 응답 상태 코드 */
    private final HttpStatus status;

    /** 사용자에게 반환할 상세 에러 메시지 */
    private final String message;
}
