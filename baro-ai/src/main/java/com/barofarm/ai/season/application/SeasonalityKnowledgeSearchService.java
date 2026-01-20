package com.barofarm.ai.season.application;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 제철 지식 검색 서비스 (RAG용)
 *
 * 상품명과 카테고리로 유사한 제철 정보를 VectorStore에서 검색
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "seasonality.rag.enabled", havingValue = "true", matchIfMissing = false)
public class SeasonalityKnowledgeSearchService {

    private final VectorStore vectorStore;
    private final int maxResults;

    public SeasonalityKnowledgeSearchService(
            @Qualifier("seasonalityVectorStore") VectorStore vectorStore,
            @Value("${seasonality.rag.max-results:3}") int maxResults) {
        this.vectorStore = vectorStore;
        this.maxResults = maxResults;
    }

    /**
     * 상품명과 카테고리로 유사한 제철 정보 검색
     *
     * 카테고리가 정확하지 않을 수 있으므로 상품명 우선으로 검색하고,
     * 카테고리 매칭은 결과 필터링에서 처리
     *
     * @param productName 상품명
     * @param category 카테고리
     * @return 유사한 제철 정보 문서 리스트 (최대 maxResults개)
     */
    public List<SeasonalityKnowledge> searchSimilarKnowledge(String productName, String category) {
        // 검색 쿼리: 상품명과 카테고리 조합 (카테고리 정보 포함으로 유사도 검색 개선)
        // 예: "오렌지 FRUIT" → 같은 카테고리 내에서 유사한 과일 찾기
        String query = String.format("%s %s", productName, category);

        log.info("제철 지식 검색: productName={}, category={}, query={}", productName, category, query);

        // VectorStore에서 유사도 검색 (상품명 + 카테고리)
        // 필터링을 고려하여 충분한 후보 확보 (maxResults * 3)
        SearchRequest searchRequest = SearchRequest.query(query)
            .withTopK(maxResults * 3);
        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        log.debug("초기 검색 결과: {}개 문서 발견", documents.size());

        // Document를 SeasonalityKnowledge로 변환
        List<SeasonalityKnowledge> knowledgeList = documents.stream()
            .map(this::toKnowledge)
            .collect(Collectors.toList());

        log.debug("초기 검색 결과 변환 완료: {}개", knowledgeList.size());

        // 1단계: 정확한 상품명 매칭 우선 (예: "쌀" 입력 시 "쌀"만 매칭, "현미쌀" 제외)
        List<SeasonalityKnowledge> exactMatch = knowledgeList.stream()
            .filter(k -> k.productName().equals(productName))
            .collect(Collectors.toList());

        if (!exactMatch.isEmpty()) {
            log.info("정확한 상품명 매칭 발견: {}개", exactMatch.size());
            return exactMatch.stream().limit(maxResults).collect(Collectors.toList());
        }

        // 2단계: 상품명이 검색 결과에 포함되는 경우 (예: "수미 감자" → "감자 수미감자")
        // 또는 검색 결과가 입력에 포함되는 경우 (예: "감귤" → "귤 감귤", "한라봉" → "귤 한라봉")
        List<SeasonalityKnowledge> containsMatch = knowledgeList.stream()
            .filter(k -> {
                // 정확한 포함 관계 확인
                boolean inputContainsResult = productName.contains(k.productName());
                boolean resultContainsInput = k.productName().contains(productName);

                return inputContainsResult || resultContainsInput;
            })
            .collect(Collectors.toList());

        if (!containsMatch.isEmpty()) {
            log.info("상품명 포함 매칭 발견: {}개", containsMatch.size());

            // 우선순위 매칭: "작물명 품종명" 패턴 우선 처리
            // 예: 입력 "한라봉" → 결과 "귤 한라봉" (결과가 "귤 "으로 시작하고 입력을 포함)
            // 예: 입력 "부사" → 결과 "사과 부사" (결과가 "사과 "로 시작하고 입력을 포함)
            // 예: 입력 "감귤" → 결과 "귤 감귤" (결과가 "귤 "으로 시작하고 입력을 포함)
            List<SeasonalityKnowledge> priorityMatch = containsMatch.stream()
                .filter(k -> {
                    String resultName = k.productName();

                    // 결과가 "작물명 품종명" 형식인지 확인 (공백으로 구분)
                    int spaceIndex = resultName.indexOf(' ');
                    if (spaceIndex > 0) {
                        String cropName = resultName.substring(0, spaceIndex);
                        String varietyName = resultName.substring(spaceIndex + 1);

                        // 입력이 품종명과 일치하거나 포함되는 경우
                        // 예: 입력 "한라봉" = 품종명 "한라봉" → 우선 매칭
                        // 예: 입력 "감귤" = 품종명 "감귤" → 우선 매칭
                        if (varietyName.equals(productName) || varietyName.contains(productName) || productName.contains(varietyName)) {
                            return true;
                        }

                        // 입력에 작물명이 포함되고 결과도 같은 작물명으로 시작하는 경우
                        // 예: 입력 "귤" → 결과 "귤 한라봉" → 우선 매칭
                        if (productName.contains(cropName) && resultName.startsWith(cropName + " ")) {
                            return true;
                        }
                    }

                    // 특수 케이스: "감귤"이 입력에 있으면 "귤 감귤" 같은 패턴 우선 매칭
                    // (위의 일반 패턴으로도 처리되지만, 명시적으로 확인)
                    // if (productName.contains("감귤") && resultName.contains("감귤")) {
                    //     return true;
                    // }

                    return false;
                })
                .collect(Collectors.toList());

            // 우선순위 매칭이 있으면 사용, 없으면 카테고리 매칭
            List<SeasonalityKnowledge> result;
            if (!priorityMatch.isEmpty()) {
                result = priorityMatch;
                log.info("우선순위 매칭 발견: {}개 (작물명 품종명 패턴)", priorityMatch.size());
            } else {
                // 카테고리 매칭 우선
                List<SeasonalityKnowledge> categoryMatched = containsMatch.stream()
                    .filter(k -> category.equalsIgnoreCase(k.category()))
                    .collect(Collectors.toList());

                result = categoryMatched.isEmpty() ? containsMatch : categoryMatched;
            }

            return result.stream().limit(maxResults).collect(Collectors.toList());
        }

        // 3단계: 카테고리 매칭 필터링 (같은 카테고리 내에서만 검색)
        // 예: "오렌지" 검색 시 같은 FRUIT 카테고리 내에서만 유사한 과일 찾기 (귤, 한라봉 등)
        List<SeasonalityKnowledge> matched = knowledgeList.stream()
            .filter(k -> category.equalsIgnoreCase(k.category()))
            .collect(Collectors.toList());

        // 같은 카테고리 내 결과만 사용 (같은 카테고리가 없으면 빈 리스트 반환)
        // 벡터 유사도 검색의 의미적 유사도를 개선하기 위해 같은 카테고리로 제한
        // 벡터 유사도 검색 결과를 신뢰 (공통 글자 수 기반 필터링 제거)
        // 예: "오렌지" 검색 시 벡터 유사도로 "귤", "한라봉" 같은 감귤류가 나올 수 있음
        //     공통 글자 수 필터링을 제거하여 의미적 유사도를 신뢰
        List<SeasonalityKnowledge> result = matched;

        // 결과 개수 제한
        if (result.size() > maxResults) {
            result = result.subList(0, maxResults);
        }

        log.info("최종 검색 결과: {}개 문서 (카테고리 매칭: {}개)",
            result.size(), matched.size());

        // 검색 결과 로깅 (디버깅용)
        result.forEach(k -> {
            log.debug("  - {} ({}): {} = {}",
                k.productName(), k.category(), k.seasonalityValue(),
                category.equalsIgnoreCase(k.category()) ? "매칭" : "불일치");
        });

        return result;
    }

    /**
     * Document를 SeasonalityKnowledge로 변환
     */
    private SeasonalityKnowledge toKnowledge(Document document) {
        var metadata = document.getMetadata();

        return new SeasonalityKnowledge(
            (String) metadata.getOrDefault("productName", ""),
            (String) metadata.getOrDefault("category", ""),
            (String) metadata.getOrDefault("seasonalityType", ""),
            (String) metadata.getOrDefault("seasonalityValue", ""),
            document.getText() // 전체 텍스트 내용 (getContent() deprecated)
        );
    }

    /**
     * 검색된 제철 지식 정보
     */
    public record SeasonalityKnowledge(
        String productName,
        String category,
        String seasonalityType,
        String seasonalityValue,
        String content
    ) {}
}
