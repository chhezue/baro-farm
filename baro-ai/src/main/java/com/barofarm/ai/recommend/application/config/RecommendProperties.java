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
     * 장바구니 품목 중 레시피 식재료로 사용하지 않을 제외 카테고리 코드 목록 (예: 주방용품, 가전 등).
     * `RecipeRecommendService`에서 장바구니 아이템 필터링 시 검사됩니다.
     */
    private List<String> excludeRecipeCategoryCodes = new ArrayList<>();
}
