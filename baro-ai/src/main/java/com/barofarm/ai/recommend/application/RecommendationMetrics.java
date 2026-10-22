package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.RecipeRecommendReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 추천이 실제로 무언가를 돌려줬는지를 지표로 남긴다.
 *
 * <p>이 서비스의 추천 API는 실패해도 4xx·5xx를 내지 않고 빈 목록으로 나간다 — 취향 데이터가 아직
 * 없거나 기준 상품에 벡터가 없는 것은 오류가 아니라 사용자 상태이기 때문이다. 그 정책은 그대로 두되,
 * 빈 응답이 얼마나 자주 나가는지는 알아야 한다. 빈 응답 비율이 갑자기 오르면 색인이 밀렸거나 벡터가
 * 비어 있다는 신호인데, 지금까지는 그 신호가 어디에도 남지 않아 화면이 비어도 아무도 몰랐다.
 *
 * <p>이름은 {@code recommendation.results}이고 태그는 추천 종류(type)와 결과(outcome)다.
 * 종류별 빈 응답 비율은 {@code outcome="empty"}를 전체로 나눠 본다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationMetrics {

    /** 취향 벡터 기반 개인화 추천 */
    public static final String PERSONALIZED = "personalized";

    /** 상품 상세의 유사 상품 추천 */
    public static final String SIMILAR = "similar";

    /** 장바구니 기반 레시피 추천 */
    public static final String RECIPE = "recipe";

    private static final String METER_NAME = "recommendation.results";

    private final MeterRegistry meterRegistry;

    /**
     * 추천 한 번의 결과를 남긴다.
     *
     * @param type        추천 종류 — 이 클래스의 상수를 쓴다
     * @param resultCount 사용자에게 나간 항목 수. 0이면 빈 응답으로 센다
     */
    public void record(String type, int resultCount) {
        Counter.builder(METER_NAME)
            .tag("type", type)
            .tag("outcome", resultCount > 0 ? "served" : "empty")
            .description("추천 API가 항목을 돌려줬는지 여부")
            .register(meterRegistry)
            .increment();
    }

    /**
     * 레시피 추천은 왜 비었는지까지 남긴다.
     *
     * 장바구니가 비어서 못 만든 것과 생성 모델이 응답하지 않아 못 만든 것은 운영자가 해야 할 일이 전혀
     * 다르다 — 앞은 정상이고 뒤는 Gemini 쪽을 봐야 한다. 응답에는 이미 이유가 실려 나가지만 그건 그 요청을
     * 받은 화면만 알 뿐이라, 비율을 보려면 지표로도 남겨야 한다.
     */
    public void recordRecipe(RecipeRecommendReason reason) {
        record(RECIPE, reason == RecipeRecommendReason.OK ? 1 : 0);

        Counter.builder(METER_NAME + ".reason")
            .tag("reason", reason.name().toLowerCase(java.util.Locale.ROOT))
            .description("레시피 추천 응답이 그 모양이 된 이유별 건수")
            .register(meterRegistry)
            .increment();
    }
}
