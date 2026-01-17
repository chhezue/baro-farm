package com.barofarm.payment.common.exception;

import com.barofarm.payment.common.response.ResponseDto;
import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseDto<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult()
            .getAllErrors()
            .get(0)
            .getDefaultMessage();

        ResponseDto<Void> body = ResponseDto.error(HttpStatus.BAD_REQUEST, message);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(body);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ResponseDto<Void>> handleCustomException(CustomException e) {
        HttpStatus status = e.getErrorCode().getStatus();

        return ResponseEntity.status(status)
            .body(ResponseDto.error(status, e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ResponseDto<Void>> handleRetryableException(RetryableException e) {

        // 외부 시스템 일시적 장애 / 타임아웃 / 네트워크 오류
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(status)
            .body(ResponseDto.error(
                status,
                "외부 시스템과의 통신이 원활하지 않습니다. 잠시 후 다시 시도해주세요."
            ));
    }
}
