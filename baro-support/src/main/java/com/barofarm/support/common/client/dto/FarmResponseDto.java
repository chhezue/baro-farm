package com.barofarm.support.common.client.dto;

/**
 * Farm API 응답 DTO (Feign 응답용)
 * baro-seller의 ResponseDto와 동일한 구조
 */
public record FarmResponseDto<T>(
    int status,
    T data,
    String message
) {
}
