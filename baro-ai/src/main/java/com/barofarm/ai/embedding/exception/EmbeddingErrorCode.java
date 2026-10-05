package com.barofarm.ai.embedding.exception;

import com.barofarm.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 임베딩 생성 및 처리 과정에서 발생하는 예외 에러 코드 열거형.
 */
@Getter
@AllArgsConstructor
public enum EmbeddingErrorCode implements BaseErrorCode {

    /** 외부 임베딩 API 호출 실패 또는 벡터 변환 오류 */
    EMBEDDING_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "임베딩 생성에 실패했습니다.");

    /** HTTP 응답 상태 코드 */
    private final HttpStatus status;

    /** 에러 상세 메시지 */
    private final String message;
}
