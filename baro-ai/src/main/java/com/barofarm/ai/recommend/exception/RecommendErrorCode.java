package com.barofarm.ai.recommend.exception;

import com.barofarm.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 추천 API 요청 파라미터 검증에서 발생하는 비즈니스 예외 코드 열거형(Enum)입니다.
 *
 * [예외 분류]
 * - 추천 개수 예외: 개인화·유사 상품 추천의 `topK`가 허용 범위(1~50)를 벗어난 경우
 *
 * 추천 실패(프로필 없음, 기준 상품 벡터 없음, LLM 호출 실패 등)는 여기에 넣지 않는다.
 * 그 경우들은 예외가 아니라 빈 목록·빈 응답으로 나가는 것이 이 도메인의 정책이다.
 */
@Getter
@AllArgsConstructor
public enum RecommendErrorCode implements BaseErrorCode {

    /** 추천 개수(topK)가 1 미만이거나 50을 초과한 경우 */
    INVALID_TOP_K(HttpStatus.BAD_REQUEST, "추천 개수는 1 이상 50 이하여야 합니다.");

    /** HTTP 응답 상태 코드 */
    private final HttpStatus status;

    /** 사용자에게 반환할 상세 에러 메시지 */
    private final String message;
}
