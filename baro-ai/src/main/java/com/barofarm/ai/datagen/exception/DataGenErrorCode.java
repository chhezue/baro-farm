package com.barofarm.ai.datagen.exception;

import com.barofarm.ai.common.exception.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DataGenErrorCode implements BaseErrorCode {
    SQL_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "SQL 파일을 찾을 수 없습니다."),
    INVALID_SQL_FORMAT(HttpStatus.BAD_REQUEST, "유효하지 않은 SQL 형식입니다."),
    DATA_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "데이터 생성에 실패했습니다."),
    INSUFFICIENT_SEED_DATA(HttpStatus.BAD_REQUEST, "시드 데이터가 부족합니다.");

    private final HttpStatus status;
    private final String message;
}
