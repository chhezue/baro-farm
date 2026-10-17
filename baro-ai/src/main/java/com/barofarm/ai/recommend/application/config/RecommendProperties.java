package com.barofarm.ai.recommend.application.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 레시피 추천 관련 application.yml 프로퍼티 설정을 바인딩하는 프로퍼티 클래스입니다.
 * 접두어: `baro.ai.recommend` (설정 파일: `classpath:recipe-recommend.yml`)
 */
@Component
@ConfigurationProperties(prefix = "baro.ai.recommend")
@Getter
@Setter
public class RecommendProperties {

    /**
     * 레시피 재료로 쓰지 않을 카테고리 코드 목록. `RecipeRecommendService`가 장바구니 품목을 거를 때 봅니다.
     * **2026-09-07부터 비어 있습니다**(`recipe-recommend.yml`) — 비어 있으면 아무것도 걸러내지 않습니다.
     * 비운 근거는 docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md에 있습니다.
     */
    private List<String> excludeRecipeCategoryCodes = new ArrayList<>();
}
