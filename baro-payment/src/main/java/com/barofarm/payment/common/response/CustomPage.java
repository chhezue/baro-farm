package com.barofarm.payment.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

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
    public static <T> CustomPage<T> from(Page<T> page) {
        return new CustomPage<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
}
