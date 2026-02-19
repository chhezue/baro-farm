package com.barofarm.order.review.presentation.dto;

import com.barofarm.order.review.application.dto.request.ReviewCreateCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReviewCreateRequest(
    @NotNull
    UUID orderItemId,

    @NotNull
    @Min(1) @Max(5)
    Integer rating,

    @NotNull
    ReviewVisibility visibility,

    @NotBlank
    String content
) {
    public ReviewCreateCommand toCommand(UUID productId, UUID userId) {
        return new ReviewCreateCommand(
            orderItemId,
            userId,
            productId,
            rating,
            visibility,
            content
        );
    }
}
