package com.barofarm.ai.season.infrastructure.knowledge;

import com.barofarm.ai.season.application.dto.SeasonalityDetectionResponse;
import com.barofarm.ai.season.infrastructure.elasticsearch.SeasonalityKeywordDocument;
import com.barofarm.ai.season.infrastructure.elasticsearch.SeasonalityKeywordRepository;
import com.barofarm.ai.season.infrastructure.embedding.SeasonalityKnowledgeDocument;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 제철 지식 데이터 저장 서비스
 *
 * LLM으로 생성된 데이터를 다음 위치에 저장:
 * 1. Elasticsearch 키워드 인덱스
 * 2. VectorStore
 * 3. CSV 파일
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "seasonality.rag.enabled", havingValue = "true", matchIfMissing = false)
public class SeasonalityKnowledgeStoreService {

    private final Optional<SeasonalityKeywordRepository> keywordRepository;
    private final VectorStore vectorStore;
    private final String csvFilePath;

    public SeasonalityKnowledgeStoreService(
            Optional<SeasonalityKeywordRepository> keywordRepository,
            @Qualifier("seasonalityVectorStore") VectorStore vectorStore,
            @Value("${seasonality.csv.path:/mnt/s3/dataset/season/seasonality-data.csv}") String csvFilePath) {
        this.keywordRepository = keywordRepository;
        this.vectorStore = vectorStore;
        this.csvFilePath = csvFilePath;
    }

    /**
     * LLM으로 생성된 제철 정보 저장
     *
     * @param detectedProductName LLM이 판단한 전체 상품명
     * @param category 카테고리
     * @param response LLM 응답
     */
    @Async
    public void storeLLMGeneratedKnowledge(
            String detectedProductName,
            String category,
            SeasonalityDetectionResponse response) {

        try {
            // 중복 체크
            if (keywordRepository.isPresent() &&
                keywordRepository.get().existsByProductNameAndCategory(detectedProductName, category)) {
                log.debug("이미 존재하는 데이터: {} ({})", detectedProductName, category);
                return;
            }

            // Repository가 없으면 저장 불가
            if (keywordRepository.isEmpty()) {
                log.warn("SeasonalityKeywordRepository가 없어 저장할 수 없습니다: {} ({})",
                    detectedProductName, category);
                return;
            }

            String seasonalityType = response.seasonalityType().name();
            String seasonalityValue = response.seasonalityValue();
            double confidence = response.confidence();
            Instant now = Instant.now();

            // LLM이 판단한 농장체험 가능 여부 사용
            String farmExperienceNote =
                response.farmExperienceNote() != null && !response.farmExperienceNote().isEmpty()
                    ? response.farmExperienceNote()
                    : "농장체험 가능";  // LLM 응답이 없으면 기본값

            // 1. Elasticsearch 키워드 인덱스에 저장
            if (keywordRepository.isPresent()) {
                saveToElasticsearchIndex(detectedProductName, category, seasonalityType,
                                       seasonalityValue, confidence, now);
            }

            // 2. VectorStore에 저장
            saveToVectorStore(detectedProductName, category, seasonalityType,
                            seasonalityValue, confidence);

            // 3. CSV 파일에 추가 (농장체험 가능 여부 포함)
            appendToCsvFile(detectedProductName, category, seasonalityType,
                          seasonalityValue, response.reasoning(), farmExperienceNote);

            log.info("LLM 생성 데이터 저장 완료: {} ({})", detectedProductName, category);

        } catch (Exception e) {
            log.error("LLM 생성 데이터 저장 실패: {} ({})", detectedProductName, category, e);
            // 저장 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
        }
    }

    /**
     * Elasticsearch 키워드 인덱스에 저장
     */
    private void saveToElasticsearchIndex(
            String productName,
            String category,
            String seasonalityType,
            String seasonalityValue,
            double confidence,
            Instant now) {

        try {
            String id = SeasonalityKeywordDocument.generateId(productName, category);

            SeasonalityKeywordDocument document = new SeasonalityKeywordDocument(
                id,
                productName,
                category,
                seasonalityType,
                seasonalityValue,
                "LLM_GENERATED",
                confidence,
                now
            );

            keywordRepository.get().save(document);
            log.debug("Elasticsearch 키워드 인덱스 저장 완료: {}", id);

        } catch (Exception e) {
            log.error("Elasticsearch 키워드 인덱스 저장 실패: {} ({})", productName, category, e);
        }
    }

    /**
     * VectorStore에 저장
     */
    private void saveToVectorStore(
            String productName,
            String category,
            String seasonalityType,
            String seasonalityValue,
            double confidence) {

        try {
            // content는 reasoning을 사용하거나 기본 텍스트 생성
            String content = String.format("%s의 제철은 %s입니다.", productName, seasonalityValue);

            Document document = SeasonalityKnowledgeDocument.createDocument(
                productName,
                category,
                content,
                seasonalityType,
                seasonalityValue,
                "LLM_GENERATED"
            );

            vectorStore.add(List.of(document));
            log.debug("VectorStore 저장 완료: {} ({})", productName, category);

        } catch (Exception e) {
            log.error("VectorStore 저장 실패: {} ({})", productName, category, e);
        }
    }

    /**
     * CSV 파일에 데이터 추가
     */
    private void appendToCsvFile(
            String productName,
            String category,
            String seasonalityType,
            String seasonalityValue,
            String reasoning,
            String farmExperienceNote) {

        try {
            Path path = Paths.get(csvFilePath);

            // 파일이 없으면 헤더 작성
            if (!Files.exists(path)) {
                try (FileWriter writer = new FileWriter(csvFilePath, false)) {
                    writer.write("productName,category,content,seasonalityType,seasonalityValue,sourceType,note\n");
                }
            }

            // CSV 라인 생성
            String content = reasoning != null && !reasoning.isEmpty()
                ? reasoning
                : String.format("%s의 제철은 %s입니다.", productName, seasonalityValue);

            // CSV 특수문자 처리: 쉼표를 다른 문자로 대체하여 일관성 유지
            // CSV 파일의 기존 형식과 일관성을 유지하기 위해 쉼표를 다른 문자로 대체
            content = normalizeCsvField(content);
            productName = normalizeCsvField(productName);

            String csvLine = String.format("%s,%s,%s,%s,%s,LLM_GENERATED,%s\n",
                productName,
                category,
                content,
                seasonalityType,
                seasonalityValue,
                farmExperienceNote
            );

            // 파일 끝에 추가 (append mode)
            try (FileWriter writer = new FileWriter(csvFilePath, true)) {
                writer.write(csvLine);
            }

            log.debug("CSV 파일 추가 완료: {} ({})", productName, category);

        } catch (IOException e) {
            log.error("CSV 파일 추가 실패: {} ({})", productName, category, e);
        }
    }

    /**
     * CSV 필드 정규화 (쉼표를 다른 문자로 대체하여 일관성 유지)
     *
     * CSV 파일의 기존 형식과 일관성을 유지하기 위해 쉼표를 다른 문자로 대체
     * 쉼표를 전각 쉼표(，) 또는 줄바꿈을 공백으로 대체
     */
    private String normalizeCsvField(String field) {
        if (field == null) {
            return "";
        }

        // 쉼표를 전각 쉼표로 대체 (CSV 필드 구분자와 충돌 방지)
        // 줄바꿈과 캐리지 리턴을 공백으로 대체
        return field.replace(",", "，")
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .trim();
    }

    /**
     * 농장체험 가능 여부 판단
     *
     * 상품명, 카테고리, reasoning을 분석하여 농장체험 가능 여부를 판단
     *
     * @param productName 상품명
     * @param category 카테고리
     * @param reasoning LLM의 reasoning (제철 설명)
     * @return "농장체험 가능" 또는 "농장체험 불가"
     */
    private String determineFarmExperienceNote(String productName, String category, String reasoning) {
        if (productName == null) {
            return "농장체험 불가";
        }

        String lowerProductName = productName.toLowerCase();
        String lowerReasoning = reasoning != null ? reasoning.toLowerCase() : "";

        // 농장체험 불가 키워드 체크
        // 수입 과일, 열대 과일, 특수 작물 등
        String[] farmExperienceImpossibleKeywords = {
            "수입", "열대", "아열대", "수입과일", "수입 과일",
            "두리안", "구아바", "망고", "파파야", "코코넛",
            "바나나", "파인애플", "키위", "아보카도"
        };

        // reasoning에서 농장체험 불가 키워드 확인
        for (String keyword : farmExperienceImpossibleKeywords) {
            if (lowerReasoning.contains(keyword) || lowerProductName.contains(keyword)) {
                log.debug("농장체험 불가 판단: {} (키워드: {})", productName, keyword);
                return "농장체험 불가";
            }
        }

        // 카테고리 기반 판단
        // FRUIT, VEGETABLE, ROOT, GRAIN, MUSHROOM 등은 일반적으로 농장체험 가능
        // ETC는 경우에 따라 다름
        if ("ETC".equalsIgnoreCase(category)) {
            // ETC 카테고리는 추가 판단 필요
            // 기본적으로 가능으로 설정하되, 키워드로 재확인
            return "농장체험 가능";
        }

        // 기본적으로 농장체험 가능
        log.debug("농장체험 가능 판단: {} ({})", productName, category);
        return "농장체험 가능";
    }
}
