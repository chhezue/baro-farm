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
 * 레시피 추천은 그 빈 응답이 왜 비었는지를 {@link com.barofarm.ai.recommend.application.dto.RecipeRecommendReason}으로
 * 200 응답 안에 실어 보낸다 — 4xx 에러 코드가 아니라 응답 필드다.
 */
@Getter
@AllArgsConstructor
public enum RecommendErrorCode implements BaseErrorCode {

    /** 추천 개수(topK)가 1 미만이거나 50을 초과한 경우. 상한은 VectorRecommendPipeline.MAX_TOP_K와 같이 움직인다 */
    INVALID_TOP_K(HttpStatus.BAD_REQUEST, "추천 개수는 1 이상 50 이하여야 합니다."),

    /**
     * 게이트웨이가 실어 보낸 사용자와 경로에 적힌 사용자가 다른 경우.
     *
     * 2026-09-08까지 이 API들은 경로에 적힌 `{userId}`를 그대로 믿었고, 그 값으로 장바구니 서비스까지
     * 조회했다. 주소창의 숫자만 바꾸면 남의 장바구니 재료로 만든 레시피를 볼 수 있었다는 뜻이다.
     * 이제 게이트웨이가 넣어 주는 `X-User-Id` 헤더가 있으면 그쪽을 신원으로 보고, 경로와 다르면 막는다.
     */
    USER_MISMATCH(HttpStatus.FORBIDDEN, "다른 사용자의 정보는 조회할 수 없습니다.");

    /** HTTP 응답 상태 코드 */
    private final HttpStatus status;

    /** 사용자에게 반환할 상세 에러 메시지 */
    private final String message;
}
