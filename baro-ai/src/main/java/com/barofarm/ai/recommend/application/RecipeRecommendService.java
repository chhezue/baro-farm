package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.application.dto.response.RecipeRecommendResponse;
import com.barofarm.ai.recommend.client.CartClient;
import com.barofarm.ai.recommend.client.dto.CartInfo;
import com.barofarm.ai.recommend.client.dto.CartItemInfo;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeRecommendService {

    private final CartClient cartClient;
    private final ChatClient chatClient;

    /**
     * 장바구니 기반 레시피 추천
     *
     * @param userId 사용자 ID
     * @return 레시피 추천 결과
     */
    public RecipeRecommendResponse recommendFromCart(UUID userId) {
        // 1. Feign 통신으로 장바구니 조회
        CartInfo cart = cartClient.getCart(userId);

        if (cart.items().isEmpty()) {
            log.warn("장바구니가 비어있습니다. userId: {}", userId);
            return new RecipeRecommendResponse("", List.of(), "");
        }

        // 2. 장바구니 상품명 추출
        List<String> productNames = cart.items().stream()
            .map(CartItemInfo::productName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .toList();

        // 3. LLM에게 식재료 추출 요청
        List<String> ingredients = extractIngredients(productNames);

        if (ingredients.isEmpty()) {
            log.warn("추출된 식재료가 없습니다. userId: {}", userId);
            return new RecipeRecommendResponse("", List.of(), "");
        }

        // 4. LLM에게 레시피 생성 요청
        RecipeIdea recipeIdea = generateRecipe(ingredients);

        // 5. 응답 생성
        return new RecipeRecommendResponse(
            recipeIdea.recipeName(),
            recipeIdea.ingredients(),
            recipeIdea.instructions()
        );
    }

    /**
     * 상품명 목록 기반 레시피 추천 (테스트용)
     * Feign 통신 없이 직접 상품명을 입력받아 레시피 추천
     *
     * @param productNames 상품명 목록
     * @return 레시피 추천 결과
     */
    public RecipeRecommendResponse testRecommendFromProductNames(List<String> productNames) {
        if (productNames == null || productNames.isEmpty()) {
            log.warn("입력된 상품명이 없습니다.");
            return new RecipeRecommendResponse("", List.of(), "");
        }

        // 입력 상품명 로깅
        log.info("테스트 레시피 추천 시작 - 상품명: {}", productNames);

        // 1. LLM에게 식재료 추출 요청
        List<String> ingredients = extractIngredients(productNames);

        if (ingredients.isEmpty()) {
            log.warn("추출된 식재료가 없습니다. 입력 상품명: {}", productNames);
            return new RecipeRecommendResponse("", List.of(), "");
        }

        log.info("추출된 식재료: {}", ingredients);

        // 2. LLM에게 레시피 생성 요청
        RecipeIdea recipeIdea = generateRecipe(ingredients);

        log.info("생성된 레시피: {}", recipeIdea.recipeName());

        // 3. 응답 생성
        return new RecipeRecommendResponse(
            recipeIdea.recipeName(),
            recipeIdea.ingredients(),
            recipeIdea.instructions()
        );
    }

    /**
     * 상품명 목록에서 식재료 추출
     *
     * @param productNames 상품명 목록
     * @return 추출된 식재료 목록
     */
    private List<String> extractIngredients(List<String> productNames) {
        // List를 ST4 안전한 문자열로 변환
        String productNamesText = productNames.stream()
            .map(name -> "- " + name)
            .collect(Collectors.joining("\n"));

        // PromptTemplate 사용 (ST4 파싱 안전)
        String templateString = """
            당신은 농산물 이커머스 플랫폼의 전문 요리 분석 AI입니다.
            사용자의 장바구니 상품명을 분석하여,
            실제 요리에 사용할 수 있는 **핵심 식재료만 선별적으로 추출**하는 역할을 담당합니다.

            <입력 데이터>
            사용자의 장바구니 상품 목록:
            {productNames}

            <핵심 정책>
            - 장바구니의 모든 상품을 반드시 사용할 필요는 없습니다.
            - 요리에 실제로 적합한 재료만 선택적으로 추출하세요.
            - 요리의 현실성과 정확성을 최우선으로 고려하세요.

            <아이템 개수별 제약 조건>
            1. 장바구니 상품이 1개인 경우:
               - 해당 상품에서 추출 가능한 식재료를 반드시 포함하세요.

            2. 장바구니 상품이 2개인 경우:
               - 두 상품 중 최소 1개 이상에서 추출된 식재료를 반드시 포함하세요.

            3. 장바구니 상품이 3개 이상인 경우:
               - 모든 상품을 사용할 필요는 없습니다.
               - 요리 기준으로 필요한 핵심 식재료를 선택하세요.
               - 필요하다면 3개 이상 선택해도 됩니다.
               - 육류 또는 수산물이 있다면 최소 1개는 우선 포함하세요.
               - 서로 어울리지 않는 재료 조합은 과감히 제외하세요.

            <식재료 추출 우선순위>
            - 육류, 수산물
            - 채소
            - 과일
            - 유제품
            - 곡물
            - 조미료

            <제외 대상>
            - 브랜드명, 수량, 단위, 지역명
            - 용기, 세트, 패키지
            - 조리도구, 생활용품

            <정규화 규칙>
            - "청송 사과" → "사과"
            - "한돈 삼겹살" → "삼겹살"
            - "국내산 배추" → "배추"
            - "유기농 토마토" → "토마토"

            <품질 보장>
            - 중복 제거
            - 실제 요리에 사용 가능한 재료만 포함
            - 한 단어 재료명만 허용

            <출력 규칙>
            - JSON 형식으로만 응답
            - ingredients 필드는 문자열 배열
            - 각 재료는 한 단어
            - 다른 텍스트 절대 포함 금지
            """;

        try {
            IngredientsResponse response = chatClient.prompt()
                .user(p -> p.text(templateString).param("productNames", productNamesText))
                .call()
                .entity(new ParameterizedTypeReference<IngredientsResponse>() {});

            return response != null && response.ingredients() != null
                ? response.ingredients().stream()
                    .filter(ingredient -> ingredient != null && !ingredient.trim().isEmpty())
                    .distinct()
                    .toList()
                : List.of();

        } catch (Exception e) {
            log.error("식재료 추출 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 식재료 목록 기반 레시피 생성
     *
     * @param ingredients 식재료 목록
     * @return 레시피 아이디어
     */
    private RecipeIdea generateRecipe(List<String> ingredients) {
        // List를 ST4 안전한 문자열로 변환
        String ingredientsText = String.join(", ", ingredients);

        // PromptTemplate 사용 (ST4 파싱 안전)
        String templateString = """
            당신은 실제 요리 레시피 DB와 가정식 요리 데이터를 학습한 전문 요리 추천 AI입니다.
            존재하지 않는 요리명이나 추상적인 요리 분류명은 절대 사용하지 마세요.

            <사용 가능한 식재료>
            {ingredients}

            <1단계: 요리 카테고리 선택>
            아래 카테고리 중 하나를 내부적으로 선택하세요 (출력하지 마세요).
            - 메인 요리 (밥/면/단백질 중심)
            - 볶음 / 조림
            - 국 / 찌개
            - 샐러드
            - 간단 반찬

            ※ 샐러드는 가능한 선택지 중 하나일 뿐, 항상 우선되지 않습니다.

            <2단계: 요리명 생성 규칙>
            - 반드시 실제로 널리 알려진 정확한 요리명만 사용하세요.
            - 다음과 같은 추상적인 명칭은 절대 사용하지 마세요:
              ❌ 채소 볶음
              ❌ 나물 무침
              ❌ 고기 요리
            - 다음과 같은 구체적인 명칭만 허용됩니다:
              ✅ 애호박 볶음
              ✅ 시금치 나물
              ✅ 연어 스테이크
              ✅ 김치찌개

            <식재료 사용 규칙>
            - 재료가 1개면 반드시 해당 재료를 사용하는 요리
            - 재료가 2개면 최소 1개 이상 사용
            - 재료가 3개 이상이면 요리에 적합한 만큼 자유롭게 사용
              (2개로 제한하지 말 것)

            <요리 품질 기준>
            - 가정식 또는 일반 음식점에서 실제로 판매되는 요리 수준
            - 억지 퓨전 금지
            - 재료 궁합이 명확해야 함

            <응답 규칙>
            - JSON 형식으로만 응답
            - recipeName: 실제 존재하는 요리명
            - ingredients: 문자열 배열
            - instructions: 단계별 조리법
            - 다른 텍스트 절대 포함 금지
            """;

        try {
            RecipeIdea recipe = chatClient.prompt()
                .user(p -> p.text(templateString).param("ingredients", ingredientsText))
                .call()
                .entity(new ParameterizedTypeReference<RecipeIdea>() {});

            return recipe != null ? recipe : RecipeIdea.empty();

        } catch (Exception e) {
            log.error("레시피 생성 실패: {}", e.getMessage(), e);
            return RecipeIdea.empty();
        }
    }

    /**
     * 식재료 추출 응답 DTO
     */
    private record IngredientsResponse(
        List<String> ingredients
    ) {
    }

    /**
     * 레시피 아이디어 DTO
     */
    private record RecipeIdea(
        String recipeName,
        List<String> ingredients,
        String instructions
    ) {
        static RecipeIdea empty() {
            return new RecipeIdea("", List.of(), "");
        }
    }
}
