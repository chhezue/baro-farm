package com.barofarm.support.common.client.dto;

import java.util.List;

/**
 * 페이지네이션 응답 DTO (Feign 응답용)
 * baro-seller의 CustomPage와 동일한 구조
 */
public record CustomPage<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    boolean hasNext,
    boolean hasPrevious
) {
}
