package com.barofarm.ai.log.exception;

import com.barofarm.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum LogErrorCode implements BaseErrorCode {
    LOG_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "로그 저장에 실패했습니다."),
    INVALID_LOG_DATA(HttpStatus.BAD_REQUEST, "유효하지 않은 로그 데이터입니다.");

    private final HttpStatus status;
    private final String message;
}
