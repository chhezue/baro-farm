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
        List<SeasonalityKnowledge> knowledgeList = performInitialSearch(productName, category);

        // 1단계: 정확한 상품명 매칭 우선
        List<SeasonalityKnowledge> exactMatch = findExactMatch(knowledgeList, productName);
        if (!exactMatch.isEmpty()) {
            return limitResults(exactMatch);
        }

        // 2단계: 상품명 포함 매칭
        List<SeasonalityKnowledge> containsMatch = findContainsMatch(knowledgeList, productName);
        if (!containsMatch.isEmpty()) {
            return processContainsMatch(containsMatch, productName, category);
        }

        // 3단계: 카테고리 매칭 필터링
        return processCategoryMatch(knowledgeList, category);
    }

    private List<SeasonalityKnowledge> performInitialSearch(String productName, String category) {
        String query = String.format("%s %s", productName, category);
        log.info("제철 지식 검색: productName={}, category={}, query={}", productName, category, query);

        SearchRequest searchRequest = SearchRequest.query(query).withTopK(maxResults * 3);
        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        log.debug("초기 검색 결과: {}개 문서 발견", documents.size());

        List<SeasonalityKnowledge> knowledgeList = documents.stream()
            .map(this::toKnowledge)
            .collect(Collectors.toList());

        log.debug("초기 검색 결과 변환 완료: {}개", knowledgeList.size());
        return knowledgeList;
    }

    private List<SeasonalityKnowledge> findExactMatch(
            List<SeasonalityKnowledge> knowledgeList, String productName) {
        List<SeasonalityKnowledge> exactMatch = knowledgeList.stream()
            .filter(k -> k.productName().equals(productName))
            .collect(Collectors.toList());

        if (!exactMatch.isEmpty()) {
            log.info("정확한 상품명 매칭 발견: {}개", exactMatch.size());
        }
        return exactMatch;
    }

    private List<SeasonalityKnowledge> findContainsMatch(
            List<SeasonalityKnowledge> knowledgeList, String productName) {
        return knowledgeList.stream()
            .filter(k -> {
                boolean inputContainsResult = productName.contains(k.productName());
                boolean resultContainsInput = k.productName().contains(productName);
                return inputContainsResult || resultContainsInput;
            })
            .collect(Collectors.toList());
    }

    private List<SeasonalityKnowledge> processContainsMatch(
            List<SeasonalityKnowledge> containsMatch,
            String productName,
            String category) {
        log.info("상품명 포함 매칭 발견: {}개", containsMatch.size());

        List<SeasonalityKnowledge> priorityMatch = findPriorityMatch(containsMatch, productName);
        if (!priorityMatch.isEmpty()) {
            log.info("우선순위 매칭 발견: {}개 (작물명 품종명 패턴)", priorityMatch.size());
            return limitResults(priorityMatch);
        }

        List<SeasonalityKnowledge> categoryMatched = containsMatch.stream()
            .filter(k -> category.equalsIgnoreCase(k.category()))
            .collect(Collectors.toList());

        List<SeasonalityKnowledge> result =
            categoryMatched.isEmpty() ? containsMatch : categoryMatched;
        return limitResults(result);
    }

    private List<SeasonalityKnowledge> findPriorityMatch(
            List<SeasonalityKnowledge> containsMatch, String productName) {
        return containsMatch.stream()
            .filter(k -> {
                String resultName = k.productName();
                int spaceIndex = resultName.indexOf(' ');
                if (spaceIndex > 0) {
                    String cropName = resultName.substring(0, spaceIndex);
                    String varietyName = resultName.substring(spaceIndex + 1);

                    if (varietyName.equals(productName)
                        || varietyName.contains(productName)
                        || productName.contains(varietyName)) {
                        return true;
                    }

                    if (productName.contains(cropName) && resultName.startsWith(cropName + " ")) {
                        return true;
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
    }

    private List<SeasonalityKnowledge> processCategoryMatch(
            List<SeasonalityKnowledge> knowledgeList, String category) {
        List<SeasonalityKnowledge> matched = knowledgeList.stream()
            .filter(k -> category.equalsIgnoreCase(k.category()))
            .collect(Collectors.toList());

        List<SeasonalityKnowledge> result = matched;
        if (result.size() > maxResults) {
            result = result.subList(0, maxResults);
        }

        log.info("최종 검색 결과: {}개 문서 (카테고리 매칭: {}개)", result.size(), matched.size());

        result.forEach(k -> {
            log.debug("  - {} ({}): {} = {}",
                k.productName(), k.category(), k.seasonalityValue(),
                category.equalsIgnoreCase(k.category()) ? "매칭" : "불일치");
        });

        return result;
    }

    private List<SeasonalityKnowledge> limitResults(List<SeasonalityKnowledge> results) {
        return results.stream().limit(maxResults).collect(Collectors.toList());
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
