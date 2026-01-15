package com.barofarm.ai.datagen.application;

import com.barofarm.ai.datagen.application.dto.AutoAmplifyResponse;
import com.barofarm.ai.datagen.application.dto.ProductGenerationDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGenerationService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SqlDataReaderService sqlDataReaderService;

    private static final int TARGET_COUNT = 10;
    private static final int MAX_ITERATIONS = 1000;
    private static final String[] VALID_CATEGORIES = {"FRUIT", "VEGETABLE", "GRAIN", "NUT", "ROOT", "MUSHROOM", "ETC"};
    private final Random random = new Random();

    @SuppressWarnings("checkstyle:MethodLength")
    public List<ProductGenerationDto> generateProducts(List<ProductGenerationDto> examples, String targetCategory) {
        final String examplesJson = serializeExamplesToJson(examples);

        final String jsonFormat = """
            [
                {
                    "productName": "string",
                    "description": "string",
                    "productCategory": "string (FRUIT, VEGETABLE, ETC...)",
                    "price": "integer",
                    "stockQuantity": 100,
                    "status": "ON_SALE"
                }
            ]
            """;

        final String promptTemplate = """
당신은 프리미엄 신선식품 이커머스의 시니어 상품 MD입니다.
실제 온라인 쇼핑몰에 등록될 상품명을 기획하는 관점으로 답변하세요.

아래는 기존 상품 데이터의 예시입니다.

[기존 상품 예시]
{examples}

위 예시의 톤과 자연스러움을 참고하여,
'{category}' 카테고리에 속하는 **새롭고 서로 다른 상품 5개**를 생성해주세요.

---

[상품 생성 가이드]

1️⃣ 상품 성격
- 반드시 **원물 상품**만 생성하세요.
- 허용: 농산물 / 수산물 / 축산물
- 금지: 가공식품 (김치, 즙, 주스, 잼, 장아찌, 손질·양념 제품 등)

2️⃣ 상품명 기획 핵심 규칙 (매우 중요)
- 각 상품명은 아래 **5가지 특징 중에서 정확히 2~3개를 랜덤하게 조합**하여 작성하세요.
- 모든 상품이 같은 조합을 사용하면 안 됩니다.
- 특정 요소(지역, 제철 등)가 모든 상품에 반복되지 않도록 주의하세요.

[추가 작성 규칙 (중요)]

- 지역명/산지는 선택 요소이며, 모든 상품에 포함할 필요는 없습니다.
- 지역명이 포함되더라도 반드시 상품명 맨 앞에 올 필요는 없습니다.

- 상품명 문장 구조가 반복되지 않도록 주의하세요.
  동일한 패턴의 상품명이 연속으로 나오면 안 됩니다.

- "엄선", "특·상급", "고당도", "아삭한", "쫀득한" 같은 형용사는
  전체 상품 중 일부에만 제한적으로 사용하세요.
  모든 상품에 비슷한 형용사가 반복되면 안 됩니다.

- 상품명에는 무게, 용량, 수량 정보를 절대 포함하지 마세요.
- kg, g, ml, L, 개, 마리, 팩, 봉, 박스 등 단위 표현은 모두 금지입니다.
- 상품명은 상품 자체의 특징만으로 완결되도록 작성하세요.

[상품명 강조 요소 5가지]

① 지역/산지
- 한국 내 실제 존재하는 지역명을 자유롭게 사용하세요.
- 예시는 참고용이며, 반드시 여기에 한정할 필요는 없습니다.
  (예: 제주, 해남, 고창, 완도, 영주, 논산, 밀양, 성주, 청도, 횡성, 태안, 보성 등)

② 품종명
- 품종이 명확한 경우 자연스럽게 포함하세요.
- 예시는 참고용이며, 반드시 여기에 한정할 필요는 없습니다.
- (예: 설향, 레드향, 천혜향, 샤인머스캣, 캠벨얼리, 베니하루카 등)

③ 맛·식감 표현
- 소비자가 바로 떠올릴 수 있는 감각적인 표현을 사용하세요.
- 예시는 참고용이며, 반드시 여기에 한정할 필요는 없습니다.
- (예: 고당도, 달달한, 아삭한, 쫀득한, 진한 풍미)

④ 품질·선별 표현
- 프리미엄 식품몰에서 자주 사용하는 자연스러운 표현을 활용하세요.
- 예시는 참고용이며, 반드시 여기에 한정할 필요는 없습니다.
- (예: 특·상급, 엄선, 산지 선별, 좋은 크기 위주)

⑤ 계절·출하 힌트 (선택 요소)
- 제철이나 출하 시기를 **은근하게 암시**하는 수준으로만 사용하세요.
- 직접적인 계절/월 언급은 피하고, 쇼핑몰 상품명처럼 자연스럽게 녹이세요.
- 예시는 참고용이며, 반드시 여기에 한정할 필요는 없습니다.
- (예: 햇출하, 노지, 월동, 봄 수확 등)

📌 자연스러운 예:
- "고당도 설향딸기 산지 선별 1kg"
- "노지에서 자란 달달한 베니하루카 고구마 2kg"
- "산지 선별로 골라낸 특·상급 샤인머스캣"
- "햇출하로 신선한 쫀득한 식감의 감귤"

📌 피해야 할 예:
- 모든 상품에 지역명이 반복되는 경우
- 제철 키워드만 강조된 기획전 스타일 상품명
- "[제철]", "[특가]" 같은 형식적인 태그 남용

3️⃣ 상품 다양성
- 5개 상품은 **강조 요소 조합, 품종, 산지, 또는 뉘앙스가 서로 달라야 합니다.**
- 같은 품목이라도 강조 포인트가 다르면 허용됩니다.

4️⃣ 결과 형식 (엄격)
- 반드시 아래 JSON 형식의 **리스트만** 반환하세요.
- 필드명, 타입을 변경하지 마세요.
- 추가 설명, 주석, 마크다운은 절대 포함하지 마세요.

{format}
""";

        return chatClient.prompt()
            .user(p -> p.text(promptTemplate)
                .param("examples", examplesJson)
                .param("category", targetCategory)
                .param("format", jsonFormat)
            )
            .call()
            .entity(new ParameterizedTypeReference<List<ProductGenerationDto>>() {});
    }

    /**
     * SQL 파일을 기반으로 자동으로 상품 데이터를 증폭
     */
    public AutoAmplifyResponse autoAmplifyProducts(String sqlFilePath) {
        try {
            log.info("자동 데이터 증폭 시작: 목표 {}개", TARGET_COUNT);

            // 1. 시드 데이터 읽기
            List<ProductGenerationDto> seedProducts = sqlDataReaderService.readProductsFromSql(sqlFilePath);

            if (seedProducts.isEmpty()) {
                log.error("시드 데이터가 없습니다: {}", sqlFilePath);
                throw new IllegalArgumentException("시드 데이터가 없습니다: " + sqlFilePath);
            }

            log.info("시드 데이터 로드 완료: {}개 상품", seedProducts.size());

            // 2. LLM으로 데이터 증폭
            List<ProductGenerationDto> amplifiedProducts = generateAmplifiedProducts(seedProducts, TARGET_COUNT);

            log.info("데이터 증폭 완료: {}개 상품 생성", amplifiedProducts.size());

            // 3. 결과를 CSV 파일로 저장
            if (amplifiedProducts.isEmpty()) {
                log.warn("⚠️ 생성된 상품이 없어 CSV 파일을 생성하지 않습니다.");
            } else {
                saveProductsToCsvFile(amplifiedProducts);
            }

            return new AutoAmplifyResponse(
                seedProducts.size(),
                TARGET_COUNT,
                amplifiedProducts.size(),
                amplifiedProducts
            );

        } catch (IOException e) {
            log.error("SQL 파일 읽기 실패: {}", sqlFilePath, e);
            throw new RuntimeException("SQL 파일 읽기 실패: " + sqlFilePath, e);
        } catch (Exception e) {
            log.error("데이터 증폭 중 오류 발생", e);
            throw new RuntimeException("데이터 증폭 중 오류 발생", e);
        }
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private List<ProductGenerationDto> generateAmplifiedProducts(
        List<ProductGenerationDto> seedProducts, int targetCount) {
        List<ProductGenerationDto> allNewProducts = new ArrayList<>();
        Set<String> generatedNames = new HashSet<>();

        // 시드 데이터에서 카테고리 추출
        List<String> categories = seedProducts.stream()
            .map(ProductGenerationDto::productCategory)
            .distinct()
            .filter(this::isValidCategory)
            .toList();

        if (categories.isEmpty()) {
            log.warn("유효한 시드 카테고리가 없습니다. 기본 카테고리를 사용합니다.");
            categories = List.of("FRUIT", "VEGETABLE", "ETC");
        }

        log.info("데이터 증폭 시작 - 목표: {}, 카테고리: {}", targetCount, categories);

        int iteration = 0;
        int consecutiveFailures = 0;
        final int maxConsecutiveFailures = 10;

        while (allNewProducts.size() < targetCount && iteration < MAX_ITERATIONS) {
            iteration++;

            // 랜덤하게 예시 선택 (최대 3개)
            List<ProductGenerationDto> examples = selectRandomExamples(seedProducts, 3);
            String targetCategory = categories.get(random.nextInt(categories.size()));

            log.debug("[{}] 카테고리: {} | 현재: {}/{}",
                iteration, targetCategory, allNewProducts.size(), targetCount);

            try {
                // LLM API 호출
                List<ProductGenerationDto> newItems = generateProducts(examples, targetCategory);

                if (newItems == null || newItems.isEmpty()) {
                    log.warn("LLM API가 빈 결과를 반환했습니다 (반복 {})", iteration);
                    consecutiveFailures++;
                    continue;
                }

                // 생성된 상품 검증 및 추가
                int addedCount = 0;
                for (ProductGenerationDto item : newItems) {
                    // stockQuantity와 status가 없으면 기본값 설정
                    ProductGenerationDto productWithDefaults = ensureDefaultValues(item);

                    if (isValidProduct(productWithDefaults)
                        && !generatedNames.contains(productWithDefaults.productName())) {
                        allNewProducts.add(productWithDefaults);
                        generatedNames.add(productWithDefaults.productName());
                        addedCount++;
                    }
                }

                if (addedCount == 0) {
                    log.warn("생성된 상품 중 유효한 상품이 없습니다 (반복 {})", iteration);
                    consecutiveFailures++;
                } else {
                    consecutiveFailures = 0; // 성공했으므로 실패 카운트 리셋
                }

                // 진행 상황 로깅 (10개마다 또는 목표 달성 시)
                if (allNewProducts.size() % 10 == 0 || allNewProducts.size() >= targetCount) {
                    double progress = (double) allNewProducts.size() / targetCount * 100;
                    log.info("진행 상황: {}/{} ({:.1f}%)", allNewProducts.size(), targetCount, progress);
                }

            } catch (Exception e) {
                log.error("LLM API 호출 실패 (반복 {}): {}", iteration, e.getMessage());
                consecutiveFailures++;

                // 연속 실패가 너무 많으면 중단
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    log.error("연속 {}회 실패로 증폭을 중단합니다", maxConsecutiveFailures);
                    break;
                }
            }

            // 너무 많은 연속 실패 방지
            if (consecutiveFailures >= maxConsecutiveFailures) {
                break;
            }
        }

        if (iteration >= MAX_ITERATIONS) {
            log.warn("최대 반복 횟수({})에 도달했습니다. 생성된 상품: {}개", MAX_ITERATIONS, allNewProducts.size());
        }

        if (allNewProducts.size() < targetCount) {
            log.warn("목표 수량에 도달하지 못했습니다. 목표: {}, 실제: {}", targetCount, allNewProducts.size());
        } else {
            log.info("목표 수량 달성: {}개 상품 생성 완료", allNewProducts.size());
        }

        return allNewProducts.subList(0, Math.min(allNewProducts.size(), targetCount));
    }

    private List<ProductGenerationDto> selectRandomExamples(List<ProductGenerationDto> seedProducts, int count) {
        List<ProductGenerationDto> examples = new ArrayList<>(seedProducts);
        int actualCount = Math.min(count, examples.size());

        List<ProductGenerationDto> selected = new ArrayList<>();
        for (int i = 0; i < actualCount; i++) {
            int randomIndex = random.nextInt(examples.size());
            selected.add(examples.get(randomIndex));
            examples.remove(randomIndex);
        }

        return selected;
    }

    private boolean isValidProduct(ProductGenerationDto product) {
        if (product == null) {
            return false;
        }

        // 상품명 검증
        String productName = product.productName();
        if (productName == null || productName.trim().isEmpty() || productName.length() > 100) {
            return false;
        }

        // 설명 검증
        String description = product.description();
        if (description == null || description.trim().isEmpty() || description.length() > 500) {
            return false;
        }

        // 카테고리 검증
        String category = product.productCategory();
        if (category == null || !isValidCategory(category)) {
            return false;
        }

        // 가격 검증 (100원 ~ 1,000,000원)
        int price = product.price();
        if (price < 100 || price > 1_000_000) {
            return false;
        }

        // 상품명에 부적절한 키워드가 없는지 확인 (선택사항)
        if (containsInappropriateContent(productName) || containsInappropriateContent(description)) {
            return false;
        }

        return true;
    }

    private boolean containsInappropriateContent(String text) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();
        // 가공식품 관련 키워드 (예시)
        String[] inappropriateKeywords = {"김치", "젓갈", "장아찌", "음료", "주스", "차", "커피", "술"};

        for (String keyword : inappropriateKeywords) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidCategory(String category) {
        for (String validCategory : VALID_CATEGORIES) {
            if (validCategory.equals(category)) {
                return true;
            }
        }
        return false;
    }

    private String serializeExamplesToJson(List<ProductGenerationDto> examples) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(examples);
        } catch (JsonProcessingException e) {
            log.error("Error serializing examples to JSON", e);
            return "[]";
        }
    }

    private void saveProductsToCsvFile(List<ProductGenerationDto> products) {
        try {
            if (products == null || products.isEmpty()) {
                log.warn("⚠️ 저장할 상품 데이터가 없습니다.");
                return;
            }

            Path outputDir = Paths.get(System.getProperty("user.dir"), "scripts", "generate-dummy");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
                log.info("📁 디렉토리 생성: {}", outputDir.toAbsolutePath());
            }
            Path outputFile = outputDir.resolve("amplified_product_data_2.csv");

            List<String> csvLines = new ArrayList<>();
            // CSV 헤더
            csvLines.add("productName,description,productCategory,price,stockQuantity,status");

            // CSV 데이터 행
            for (ProductGenerationDto product : products) {
                // 기본값 보장
                ProductGenerationDto productWithDefaults = ensureDefaultValues(product);

                String productName = escapeCsvField(productWithDefaults.productName());
                String description = escapeCsvField(productWithDefaults.description());
                String category = productWithDefaults.productCategory();
                int price = productWithDefaults.price();
                int stockQuantity = productWithDefaults.stockQuantity() != null
                    ? productWithDefaults.stockQuantity() : 100;
                String status = productWithDefaults.status() != null && !productWithDefaults.status().isEmpty()
                    ? productWithDefaults.status() : "ON_SALE";

                csvLines.add(String.format("%s,%s,%s,%d,%d,%s",
                    productName, description, category, price, stockQuantity, status));
            }

            Files.writeString(outputFile, String.join("\n", csvLines));
            log.info("✅ CSV 결과 파일 저장 완료: {} ({}개 상품)", outputFile.toAbsolutePath(), products.size());

        } catch (IOException e) {
            log.error("❌ CSV 결과 파일 저장 실패", e);
            throw new RuntimeException("Failed to save CSV file", e);
        } catch (Exception e) {
            log.error("❌ CSV 파일 저장 중 예상치 못한 오류 발생", e);
            throw new RuntimeException("Failed to save CSV file", e);
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // CSV에서 쉼표, 따옴표, 줄바꿈이 포함된 경우 처리
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            // 따옴표를 두 개로 이스케이프하고 전체를 따옴표로 감싸기
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * ProductGenerationDto에 stockQuantity와 status 기본값이 없으면 설정
     */
    private ProductGenerationDto ensureDefaultValues(ProductGenerationDto product) {
        if (product == null) {
            return null;
        }

        // 이미 기본값이 있으면 그대로 반환
        if (product.stockQuantity() != null && product.status() != null) {
            return product;
        }

        // 기본값 설정
        Integer stockQuantity = product.stockQuantity() != null ? product.stockQuantity() : 100;
        String status = product.status() != null && !product.status().isEmpty() ? product.status() : "ON_SALE";

        return new ProductGenerationDto(
            product.productName(),
            product.description(),
            product.productCategory(),
            product.price(),
            stockQuantity,
            status
        );
    }
}
