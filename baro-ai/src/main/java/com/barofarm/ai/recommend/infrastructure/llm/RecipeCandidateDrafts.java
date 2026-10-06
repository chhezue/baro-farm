package com.barofarm.ai.recommend.infrastructure.llm;

import java.util.List;

/**
 * Gemini LLM으로부터 수신하는 최상위 레시피 후보군 응답 DTO 래퍼 레코드입니다.
 *
 * [역할]
 * - Gemini API의 Structured Outputs 응답 루트 객체로 사용되며, 복수의 레시피 후보(List of RecipeCandidateDraft)를 감싸는 구조를 가집니다.
 *
 * @param candidates 생성된 레시피 초안 목록
 */
public record RecipeCandidateDrafts(List<RecipeCandidateDraft> candidates) { }
