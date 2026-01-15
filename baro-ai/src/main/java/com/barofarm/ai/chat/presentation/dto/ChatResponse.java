package com.barofarm.ai.chat.presentation.dto;

/**
 * 챗봇 응답 DTO
 */
public record ChatResponse(
    String message,
    String timestamp
) {
}
