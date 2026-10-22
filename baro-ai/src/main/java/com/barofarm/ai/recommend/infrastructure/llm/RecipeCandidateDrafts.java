package com.barofarm.ai.recommend.infrastructure.llm;

import java.util.List;

/**
 * Gemini LLM으로부터 수신하는 최상위 레시피 후보군 응답 DTO 래퍼 레코드입니다.
 *
 * [역할]
 * - Gemini API의 Structured Outputs 응답 루트 객체입니다. 프롬프트는 후보를 1개만 요구하고 실제로도 1개가
 *   옵니다(골든셋 168회 전부 candidate_count=1). 그래도 배열로 받는 이유는 이름이 빈 항목을 걸러 낼 자리를
 *   두기 위해서입니다 — 생성기가 이름이 채워진 첫 후보를 고릅니다.
 *
 * @param candidates 생성된 레시피 초안 목록
 */
public record RecipeCandidateDrafts(List<RecipeCandidateDraft> candidates) { }
