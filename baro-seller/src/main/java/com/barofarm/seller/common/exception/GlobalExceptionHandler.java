package com.barofarm.seller.common.exception;

import com.barofarm.dto.ResponseDto;
import com.barofarm.exception.BaseExceptionHandler;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    // 컨트롤러 메서드 파라미터 타입 불일치 처리
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, ConversionFailedException.class })
    public ResponseEntity<ResponseDto<Void>> handleTypeMismatch(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ResponseDto.error(HttpStatus.BAD_REQUEST, "잘못된 파라미터 형식: " + e.getMessage()));
    }
}
