package com.barofarm.ai.embedding.exception;

import com.barofarm.ai.common.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum EmbeddingErrorCode implements BaseErrorCode {
    INSUFFICIENT_LOGS(HttpStatus.BAD_REQUEST, "임베딩 생성을 위한 충분한 로그가 없습니다."),
    EMBEDDING_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "임베딩 생성에 실패했습니다."),
    USER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 프로필을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
