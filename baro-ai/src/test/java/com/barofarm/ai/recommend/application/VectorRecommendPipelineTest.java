package com.barofarm.ai.recommend.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barofarm.ai.recommend.exception.RecommendErrorCode;
import com.barofarm.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 추천 개수(topK) 검증 경계를 고정한다 — 1~33만 통과, 나머지는 INVALID_TOP_K.
 *
 * 상한 33은 임의의 숫자가 아니라 후보 상한(100)을 다양화 배수(3)로 나눈 값이다. 34부터는 후보 수가
 * 100에서 잘려 실효 배수가 3 아래로 떨어지고, 그러면 "×3이면 같은 카테고리 쌍 11.5%"라는 근거가
 * 성립하지 않는다. 그 지점을 이 테스트가 고정한다.
 * 근거: docs/audits/vector-similarity/003-embedding-dims-and-candidate-multiplier.md A-2
 */
class VectorRecommendPipelineTest {

    @Test
    @DisplayName("validateTopK — 1과 33은 통과한다")
    void validateTopK_boundariesAllowed() {
        assertThatCode(() -> VectorRecommendPipeline.validateTopK(1)).doesNotThrowAnyException();
        assertThatCode(() -> VectorRecommendPipeline.validateTopK(33)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTopK — 0·음수·34는 INVALID_TOP_K")
    void validateTopK_outOfRangeRejected() {
        for (int topK : new int[] {0, -1, 34, 51, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> VectorRecommendPipeline.validateTopK(topK))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(RecommendErrorCode.INVALID_TOP_K);
        }
    }
}
