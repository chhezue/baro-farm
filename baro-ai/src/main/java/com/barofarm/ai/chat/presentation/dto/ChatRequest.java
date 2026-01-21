package com.barofarm.ai.chat.presentation.dto;

/**
 * 챗봇 요청 DTO
 */
public record ChatRequest(
    String message,
    String userId
) {
}
