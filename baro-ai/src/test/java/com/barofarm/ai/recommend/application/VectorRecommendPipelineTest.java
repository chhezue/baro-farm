package com.barofarm.ai.recommend.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barofarm.ai.recommend.exception.RecommendErrorCode;
import com.barofarm.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 추천 개수(topK) 검증 경계를 고정한다 — 1~50만 통과, 나머지는 INVALID_TOP_K.
 */
class VectorRecommendPipelineTest {

    @Test
    @DisplayName("validateTopK — 1과 50은 통과한다")
    void validateTopK_boundariesAllowed() {
        assertThatCode(() -> VectorRecommendPipeline.validateTopK(1)).doesNotThrowAnyException();
        assertThatCode(() -> VectorRecommendPipeline.validateTopK(50)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTopK — 0·음수·51은 INVALID_TOP_K")
    void validateTopK_outOfRangeRejected() {
        for (int topK : new int[] {0, -1, 51, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> VectorRecommendPipeline.validateTopK(topK))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(RecommendErrorCode.INVALID_TOP_K);
        }
    }
}
