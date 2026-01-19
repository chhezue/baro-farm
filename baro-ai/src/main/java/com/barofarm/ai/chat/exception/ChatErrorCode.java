package com.barofarm.ai.chat.exception;

import com.barofarm.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "유효하지 않은 메시지입니다."),
    CHAT_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "챗봇 처리에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
