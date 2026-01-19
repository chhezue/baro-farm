package com.barofarm.ai.season.application;

import com.barofarm.ai.season.application.dto.SeasonalityDetectionResponse;
import com.barofarm.ai.season.application.dto.SeasonalityInfo;
import com.barofarm.ai.season.application.dto.SeasonalityUpdateRequest;
// import com.barofarm.ai.season.domain.SeasonalityDetectionLog;
import com.barofarm.ai.season.domain.SeasonalityType;
import com.barofarm.ai.season.infrastructure.client.ProductUpdateFeignClient;
import com.barofarm.ai.season.infrastructure.knowledge.SeasonalityKnowledgeStoreService;
// import com.barofarm.ai.season.infrastructure.repository.SeasonalityDetectionLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SeasonalityDetectionService {

    private final ChatModel chatModel;
    // private final Optional<SeasonalityDetectionLogRepository> logRepository;
    private final ObjectMapper objectMapper;
    private final Optional<SeasonalityKnowledgeSearchService> knowledgeSearchService;
    private final Optional<SeasonalityKnowledgeStoreService> knowledgeStoreService;
    private final ProductUpdateFeignClient productUpdateClient;
    private final boolean useCompactFormat;
    
    // JSON 추출을 위한 정규식 패턴
    private static final Pattern JSON_PATTERN = Pattern.compile(
        "\\{.*\"seasonalityType\".*\\}", 
        Pattern.DOTALL
    );

    public SeasonalityDetectionService(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            // Optional<SeasonalityDetectionLogRepository> logRepository,
            ObjectMapper objectMapper,
            Optional<SeasonalityKnowledgeSearchService> knowledgeSearchService,
            Optional<SeasonalityKnowledgeStoreService> knowledgeStoreService,
            ProductUpdateFeignClient productUpdateClient,
            @Value("${seasonality.rag.compact-format:true}") boolean useCompactFormat) {
        this.chatModel = chatModel;
        // this.logRepository = logRepository;
        this.objectMapper = objectMapper;
        this.knowledgeSearchService = knowledgeSearchService;
        this.knowledgeStoreService = knowledgeStoreService;
        this.productUpdateClient = productUpdateClient;
        this.useCompactFormat = useCompactFormat;
    }

    /**
     * 비동기로 제철 판단 수행
     *
     * @param productId   상품 ID
     * @param productName 상품명
     * @param category    카테고리
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<Void> detectSeasonalityAsync(
            UUID productId,
            String productName,
            String category) {

        try {
            log.info("제철 판단 시작: productId={}, productName={}, category={}", 
                productId, productName, category);

            SeasonalityInfo seasonalityInfo;
            SeasonalityDetectionResponse response = null;
            
            // RAG 검색 결과를 변수에 저장 (중복 검색 방지)
            List<SeasonalityKnowledgeSearchService.SeasonalityKnowledge> knowledgeList = null;
            if (knowledgeSearchService.isPresent()) {
                knowledgeList = knowledgeSearchService.get()
                    .searchSimilarKnowledge(productName, category);
            }

            // 1. RAG 검색으로 정확한 매칭 확인 (LLM 호출 없이 직접 반환 가능)
            if (knowledgeList != null && !knowledgeList.isEmpty()) {
                String matchedProductName = knowledgeList.get(0).productName();
                String matchedCategory = knowledgeList.get(0).category();
                String matchedValue = knowledgeList.get(0).seasonalityValue();
                String matchedType = knowledgeList.get(0).seasonalityType();
                
                // 정확한 매칭: LLM 호출 없이 직접 반환 (CSV 데이터가 정답)
                boolean isExactMatch = matchedProductName.equals(productName) && 
                                      matchedCategory.equalsIgnoreCase(category);
                
                if (isExactMatch) {
                    log.info("RAG 정확한 매칭 발견 - LLM 호출 생략: productId={}, productName={}, matched={}", 
                             productId, productName, matchedProductName);
                    
                    SeasonalityType seasonalityType = SeasonalityType.valueOf(matchedType);
                    seasonalityInfo = new SeasonalityInfo(
                        matchedProductName,  // CSV의 정확한 상품명
                        matchedCategory,
                        seasonalityType,
                        matchedValue,
                        0.95,  // 정확한 매칭이므로 높은 신뢰도
                        LocalDateTime.now()
                    );
                    
                    // buyer-service에 제철 정보 업데이트 요청
                    SeasonalityUpdateRequest updateRequest = new SeasonalityUpdateRequest(
                        seasonalityInfo.type(),
                        seasonalityInfo.value()
                    );
                    productUpdateClient.updateSeasonality(productId, updateRequest);
                    log.debug("제철 정보 업데이트 완료: type={}, value={}", 
                        updateRequest.seasonalityType(), updateRequest.seasonalityValue());
                    
                    // 로그 저장 (LLM 응답 없음으로 처리)
                    // saveLog(productId, productName, category, null, seasonalityInfo, SeasonalityDetectionLog.DetectionStatus.SUCCESS);
                    
                    log.info("제철 판단 완료 (RAG 직접 반환): productId={}, seasonality={}, confidence={}", 
                        productId, seasonalityInfo.value(), seasonalityInfo.confidence());
                    
                    return CompletableFuture.completedFuture(null);
                }
                
                // 부분/유사 매칭: LLM 호출 필요 (buildSeasonalityPrompt에서 처리)
                log.debug("RAG 매칭 발견 - LLM 호출로 확장/판단: productId={}, productName={}, matched={}", 
                         productId, productName, matchedProductName);
            }

            // 2. LLM 호출하여 제철 판단 (RAG 매칭 없음 또는 부분/유사 매칭)
            // 이미 검색한 knowledgeList를 전달하여 중복 검색 방지
            response = detectSeasonalityWithLLM(productName, category, knowledgeList);
            
            // 3. RAG 기반 confidence 계산 (이미 검색한 knowledgeList 재사용)
            double calculatedConfidence = calculateConfidenceBasedOnRAG(
                productName,
                category,
                response,
                knowledgeList  // 중복 검색 없이 재사용
            );
            
            // 4. 신뢰도 검증 (임계값: 0.7)
            if (calculatedConfidence < 0.7) {
                log.warn("제철 판단 신뢰도 낮음: productId={}, RAG confidence={}, LLM confidence={}", 
                    productId, calculatedConfidence, response.confidence());
                // saveLog(productId, productName, category, response, null, SeasonalityDetectionLog.DetectionStatus.FAILED);
                return CompletableFuture.completedFuture(null);
            }

            // 5. 제철 정보 생성 (RAG 기반 confidence 사용)
            seasonalityInfo = new SeasonalityInfo(
                response.detectedProductName(),  // LLM이 판단한 전체 상품명
                category,
                response.seasonalityType(),
                response.seasonalityValue(),
                calculatedConfidence,  // RAG 기반 confidence 사용
                LocalDateTime.now()
            );
            
            // 6. LLM 생성 데이터 저장 (Elasticsearch 인덱스 + VectorStore + CSV)
            // 단, RAG 결과와 동일한 경우 저장 생략 (이미 데이터베이스에 있음)
            boolean shouldStore = shouldStoreLLMResponse(response, knowledgeList);
            if (shouldStore && knowledgeStoreService.isPresent()) {
                knowledgeStoreService.get().storeLLMGeneratedKnowledge(
                    response.detectedProductName(),
                    category,
                    response
                );
            } else if (!shouldStore) {
                log.debug("LLM 응답이 RAG 결과와 동일 - 저장 생략: {} ({})", 
                    response.detectedProductName(), category);
            }

            // buyer-service에 제철 정보 업데이트 요청
            SeasonalityUpdateRequest updateRequest = new SeasonalityUpdateRequest(
                seasonalityInfo.type(),
                seasonalityInfo.value()
            );
            productUpdateClient.updateSeasonality(productId, updateRequest);
            log.debug("제철 정보 업데이트 완료: type={}, value={}", 
                updateRequest.seasonalityType(), updateRequest.seasonalityValue());

            // 7. 로그 저장
            // saveLog(productId, productName, category, response, seasonalityInfo, SeasonalityDetectionLog.DetectionStatus.SUCCESS);

            log.info("제철 판단 완료: productId={}, seasonality={}, RAG confidence={}, LLM confidence={}", 
                productId, response.seasonalityValue(), calculatedConfidence, response.confidence());

        } catch (Exception e) {
            log.error("제철 판단 실패: productId={}", productId, e);
            // saveLog(productId, productName, category, null, null, SeasonalityDetectionLog.DetectionStatus.FAILED);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 제철 판단 로그 저장
     *
     * @param productId        상품 ID
     * @param productName      상품명
     * @param category         카테고리
     * @param response         LLM 응답 (실패 시 null)
     * @param seasonalityInfo  제철 정보 (실패 시 null)
     * @param status           상태
     */
    /*
    private void saveLog(
            UUID productId,
            String productName,
            String category,
            SeasonalityDetectionResponse response,
            SeasonalityInfo seasonalityInfo,
            SeasonalityDetectionLog.DetectionStatus status) {

        try {
            var logBuilder = SeasonalityDetectionLog.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .productName(productName)
                .productCategory(category)
                .status(status);

            if (response != null) {
                logBuilder
                    .detectedType(response.seasonalityType())
                    .detectedValue(response.seasonalityValue())
                    .confidence(response.confidence())
                    .reasoning(response.reasoning())
                    .llmResponse(response.reasoning());  // TODO: 실제 LLM 원본 응답 저장
            }

            logRepository.ifPresentOrElse(
                repo -> {
                    try {
                        repo.save(logBuilder.build());
                        log.debug("제철 판단 로그 저장 완료: productId={}, status={}", productId, status);
                    } catch (Exception ex) {
                        log.warn("제철 판단 로그 저장 실패 (무시): productId={}", productId, ex);
                    }
                },
                () -> log.debug("제철 판단 로그 저장 스킵 (Repository 미사용): productId={}", productId)
            );

        } catch (Exception e) {
            log.error("제철 판단 로그 저장 실패: productId={}", productId, e);
            // 로그 저장 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
        }
    }
    */

    /**
     * 동기적으로 제철 판단 수행 (테스트용)
     *
     * @param productName 상품명
     * @param category    카테고리
     * @return 제철 판단 결과
     */
    public SeasonalityInfo detectSeasonality(String productName, String category) {
        log.info("제철 판단 시작 (동기): productName={}, category={}", productName, category);

        // RAG 검색 결과를 변수에 저장 (중복 검색 방지)
        List<SeasonalityKnowledgeSearchService.SeasonalityKnowledge> knowledgeList = null;
        if (knowledgeSearchService.isPresent()) {
            knowledgeList = knowledgeSearchService.get()
                .searchSimilarKnowledge(productName, category);
        }

        // 1. RAG 검색으로 정확한 매칭 확인 (LLM 호출 없이 직접 반환 가능)
        if (knowledgeList != null && !knowledgeList.isEmpty()) {
            String matchedProductName = knowledgeList.get(0).productName();
            String matchedCategory = knowledgeList.get(0).category();
            String matchedValue = knowledgeList.get(0).seasonalityValue();
            String matchedType = knowledgeList.get(0).seasonalityType();
            
            // 정확한 매칭: LLM 호출 없이 직접 반환 (CSV 데이터가 정답)
            boolean isExactMatch = matchedProductName.equals(productName) && 
                                  matchedCategory.equalsIgnoreCase(category);
            
            if (isExactMatch) {
                log.info("RAG 정확한 매칭 발견 - LLM 호출 생략: productName={}, matched={}", 
                         productName, matchedProductName);
                
                SeasonalityType seasonalityType = SeasonalityType.valueOf(matchedType);
                return new SeasonalityInfo(
                    matchedProductName,  // CSV의 정확한 상품명
                    matchedCategory,
                    seasonalityType,
                    matchedValue,
                    0.95,  // 정확한 매칭이므로 높은 신뢰도
                    LocalDateTime.now()
                );
            }
            
            // 부분/유사 매칭: LLM 호출 필요 (buildSeasonalityPrompt에서 처리)
            log.debug("RAG 매칭 발견 - LLM 호출로 확장/판단: productName={}, matched={}", 
                     productName, matchedProductName);
        }

        // 2. LLM 호출하여 제철 판단 (RAG 매칭 없음 또는 부분/유사 매칭)
        // 이미 검색한 knowledgeList를 전달하여 중복 검색 방지
        SeasonalityDetectionResponse response = detectSeasonalityWithLLM(productName, category, knowledgeList);
        
        // 3. RAG 기반 confidence 계산 (이미 검색한 knowledgeList 재사용)
        double calculatedConfidence = calculateConfidenceBasedOnRAG(
            productName,
            category,
            response,
            knowledgeList  // 중복 검색 없이 재사용
        );
        
        // 4. 신뢰도 검증 (임계값: 0.7)
        if (calculatedConfidence < 0.7) {
            log.warn("제철 판단 신뢰도 낮음: productName={}, RAG confidence={}, LLM confidence={}", 
                productName, calculatedConfidence, response.confidence());
            throw new RuntimeException(
                String.format("제철 판단 신뢰도가 낮습니다: RAG confidence=%.2f, LLM confidence=%.2f", 
                    calculatedConfidence, response.confidence())
            );
        }

        // 5. LLM 생성 데이터 저장 (Elasticsearch 인덱스 + VectorStore + CSV)
        // 단, RAG 결과와 동일한 경우 저장 생략 (이미 데이터베이스에 있음)
        boolean shouldStore = shouldStoreLLMResponse(response, knowledgeList);
        if (shouldStore && knowledgeStoreService.isPresent()) {
            knowledgeStoreService.get().storeLLMGeneratedKnowledge(
                response.detectedProductName(),
                category,
                response
            );
        } else if (!shouldStore) {
            log.debug("LLM 응답이 RAG 결과와 동일 - 저장 생략: {} ({})", 
                response.detectedProductName(), category);
        }

        // 6. 제철 정보 생성 (RAG 기반 confidence 사용)
        return new SeasonalityInfo(
            response.detectedProductName(),  // LLM이 판단한 전체 상품명 (예: "귤 타이벡")
            category,
            response.seasonalityType(),
            response.seasonalityValue(),
            calculatedConfidence,  // RAG 기반 confidence 사용
            LocalDateTime.now()
        );
    }

    /**
     * LLM을 사용하여 제철 판단
     *
     * @param productName 상품명
     * @param category    카테고리
     * @param knowledgeList 이미 검색한 RAG 결과 (null 가능, 중복 검색 방지)
     * @return 제철 판단 결과
     */
    private SeasonalityDetectionResponse detectSeasonalityWithLLM(
            String productName,
            String category,
            List<SeasonalityKnowledgeSearchService.SeasonalityKnowledge> knowledgeList) {

        String promptText = buildSeasonalityPrompt(productName, category, knowledgeList);

        // Spring AI를 사용한 응답 요청
        // ChatModel은 자동으로 Bean으로 생성되며, OpenAI 설정을 사용
        Prompt prompt = new Prompt(promptText);
        ChatResponse chatResponse = chatModel.call(prompt);
        String response = chatResponse.getResult().getOutput().getContent();

        log.debug("LLM 응답: {}", response);

        // JSON 파싱 (productName을 전달하여 기본값으로 사용)
        return parseLLMResponse(response, productName);
    }

    /**
     * 제철 판단 프롬프트 생성 (토큰 최소화)
     * 
     * RAG가 활성화된 경우 CSV 데이터셋에서 검색된 정보를 프롬프트에 포함
     * 
     * @param productName 상품명 (품종명일 수 있음, 예: "타이벡", "천혜향")
     * @param category    카테고리
     * @param knowledgeList 이미 검색한 RAG 결과 (null이면 내부에서 검색)
     * @return 간결한 프롬프트 텍스트
     */
    private String buildSeasonalityPrompt(
            String productName, 
            String category,
            List<SeasonalityKnowledgeSearchService.SeasonalityKnowledge> knowledgeList) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // RAG: 검색된 제철 지식이 있으면 프롬프트에 포함
        // knowledgeList가 null이면 내부에서 검색 (하위 호환성)
        if (knowledgeList == null && knowledgeSearchService.isPresent()) {
            log.debug("RAG 검색 서비스 존재 여부: {}", knowledgeSearchService.isPresent());
            log.info("RAG 검색 시작: productName={}, category={}", productName, category);
            knowledgeList = knowledgeSearchService.get().searchSimilarKnowledge(productName, category);
            log.info("RAG 검색 완료: 결과 {}개", knowledgeList != null ? knowledgeList.size() : 0);
        }
        
        if (knowledgeList != null && !knowledgeList.isEmpty()) {
                log.info("RAG 검색 결과 {}개를 프롬프트에 포함", knowledgeList.size());
                
                if (useCompactFormat) {
                    // 최적화된 간결 형식: 제철 값만 포함 (토큰 절감)
                    // 검색 결과를 명시적으로 참조하도록 강조
                    promptBuilder.append("참고 제철 (우선 사용): ");
                    knowledgeList.forEach(knowledge -> {
                        promptBuilder.append(String.format("%s(%s)=%s ", 
                            knowledge.productName(), 
                            knowledge.category(),
                            knowledge.seasonalityValue()));
                    });
                    promptBuilder.append("\n");
                    
                    // 검색 결과와 일치하는 상품명 명시
                    String matchedProductName = knowledgeList.get(0).productName();
                    String matchedCategory = knowledgeList.get(0).category();
                    String matchedValue = knowledgeList.get(0).seasonalityValue();
                    String matchedType = knowledgeList.get(0).seasonalityType();
                    
                    // 정확한 매칭 여부 확인
                    boolean isExactMatch = matchedProductName.equals(productName) && 
                                          matchedCategory.equalsIgnoreCase(category);
                    boolean isContainsMatch = (matchedProductName.contains(productName) || 
                                             productName.contains(matchedProductName)) &&
                                            matchedCategory.equalsIgnoreCase(category);
                    
                    if (isExactMatch) {
                        // 정확한 매칭: RAG 결과를 그대로 사용
                        promptBuilder.append(String.format(
                            "입력 '%s'는 '%s(%s)'와 정확히 일치합니다.\n\n",
                            productName, matchedProductName, matchedCategory));
                        
                        promptBuilder.append(String.format(
                            "%s(%s)의 제철을 JSON으로 응답:\n" +
                            "위 참고 정보를 그대로 사용하세요. detectedProductName='%s', seasonalityValue='%s', seasonalityType='%s'\n" +
                            "{\"detectedProductName\":\"%s\"," +
                            "\"seasonalityType\":\"%s\"," +
                            "\"seasonalityValue\":\"%s\"," +
                            "\"confidence\":0.95,\"reasoning\":\"참고 정보와 일치\"}",
                            productName, category,
                            matchedProductName, matchedValue, matchedType,
                            matchedProductName,
                            matchedType,
                            matchedValue
                        ));
                    } else if (isContainsMatch) {
                        // 부분 매칭: 품종명 확장 가능 (예: "수미 감자" → "감자 수미감자")
                        promptBuilder.append(String.format(
                            "입력 '%s'는 '%s(%s)'의 품종으로 보입니다. 품종명을 전체상품명으로 확장하세요.\n\n",
                            productName, matchedProductName, matchedCategory));
                        
                        promptBuilder.append(String.format(
                            "%s(%s)의 제철을 JSON으로 응답:\n" +
                            "품종명을 전체상품명으로 확장: detectedProductName='%s', seasonalityValue='%s', seasonalityType='%s'\n" +
                            "{\"detectedProductName\":\"%s\"," +
                            "\"seasonalityType\":\"%s\"," +
                            "\"seasonalityValue\":\"%s\"," +
                            "\"confidence\":0.9,\"reasoning\":\"품종명 확장\"}",
                            productName, category,
                            matchedProductName, matchedValue, matchedType,
                            matchedProductName,
                            matchedType,
                            matchedValue
                        ));
                    } else {
                        // 유사 매칭: 참고만 하고 LLM이 판단
                        // 입력과 결과가 완전히 다른 경우 (예: "오렌지"와 "석류") 오타 교정하지 않음
                        // 참고 정보는 참고만 하고, 입력된 상품명을 우선 사용
                        promptBuilder.append(String.format(
                            "입력 '%s'와 유사한 '%s(%s)' 정보가 있습니다.\n" +
                            "**중요**: 입력된 상품명 '%s'가 참고 정보와 완전히 다른 작물이면, 참고 정보를 무시하고 입력된 상품명 '%s'의 제철을 직접 판단하세요.\n" +
                            "참고 정보는 단지 참고용이며, 입력된 상품명이 정확합니다.\n" +
                            "참고 정보의 상품명이 입력과 의미적으로 유사하거나 포함 관계가 있으면, 참고 정보의 상품명을 우선 사용하세요.\n\n",
                            productName, matchedProductName, matchedCategory, productName, productName));
                        
                        promptBuilder.append(String.format(
                            "%s(%s)의 제철을 JSON으로 응답:\n" +
                            "참고 정보의 상품명이 입력과 의미적으로 유사하면 참고 정보의 상품명을 사용하세요. 그렇지 않으면 입력된 상품명을 그대로 사용하세요.\n" +
                            "또한 한국에서 농장체험 가능 여부를 판단하세요. (수입 과일, 열대 과일은 불가, 국내 재배 가능한 작물은 가능)\n" +
                            "{\"detectedProductName\":\"전체상품명\"," +
                            "\"seasonalityType\":\"MONTH_RANGE|YEAR_ROUND\"," +
                            "\"seasonalityValue\":\"1-3 형식(예:11-2) 또는 연중\"," +
                            "\"confidence\":0.0~1.0," +
                            "\"reasoning\":\"간단설명\"," +
                            "\"farmExperienceNote\":\"농장체험 가능 또는 농장체험 불가\"}",
                            productName, category
                        ));
                    }
                } else {
                    // 전체 형식: content 포함
                    promptBuilder.append("참고 제철 정보 (우선 사용):\n");
                    knowledgeList.forEach(knowledge -> {
                        promptBuilder.append(String.format(
                            "- %s(%s): %s (제철: %s)\n",
                            knowledge.productName(),
                            knowledge.category(),
                            knowledge.content(),
                            knowledge.seasonalityValue()
                        ));
                    });
                    
                    // 검색 결과와 일치하는 상품명 명시
                    String matchedProductName = knowledgeList.get(0).productName();
                    String matchedCategory = knowledgeList.get(0).category();
                    String matchedValue = knowledgeList.get(0).seasonalityValue();
                    String matchedType = knowledgeList.get(0).seasonalityType();
                    
                    // 정확한 매칭 여부 확인
                    boolean isExactMatch = matchedProductName.equals(productName) && 
                                          matchedCategory.equalsIgnoreCase(category);
                    boolean isContainsMatch = (matchedProductName.contains(productName) || 
                                             productName.contains(matchedProductName)) &&
                                            matchedCategory.equalsIgnoreCase(category);
                    
                    if (isExactMatch) {
                        // 정확한 매칭: RAG 결과를 그대로 사용
                        promptBuilder.append(String.format(
                            "입력 '%s'는 '%s(%s)'와 정확히 일치합니다.\n\n",
                            productName, matchedProductName, matchedCategory));
                        
                        promptBuilder.append(String.format(
                            "%s(%s)의 제철을 JSON으로 응답:\n" +
                            "위 참고 정보를 그대로 사용하세요. detectedProductName='%s', seasonalityValue='%s', seasonalityType='%s'\n" +
                            "또한 한국에서 농장체험 가능 여부를 판단하세요. (수입 과일, 열대 과일은 불가, 국내 재배 가능한 작물은 가능)\n" +
                            "{\"detectedProductName\":\"%s\"," +
                            "\"seasonalityType\":\"%s\"," +
                            "\"seasonalityValue\":\"%s\"," +
                            "\"confidence\":0.95," +
                            "\"reasoning\":\"참고 정보와 일치\"," +
                            "\"farmExperienceNote\":\"농장체험 가능 또는 농장체험 불가\"}",
                            productName, category,
                            matchedProductName, matchedValue, matchedType,
                            matchedProductName,
                            matchedType,
                            matchedValue
                        ));
                    } else if (isContainsMatch) {
                        // 부분 매칭: 품종명 확장 가능 (예: "수미 감자" → "감자 수미감자")
                        promptBuilder.append(String.format(
                            "입력 '%s'는 '%s(%s)'의 품종으로 보입니다. 품종명을 전체상품명으로 확장하세요.\n\n",
                            productName, matchedProductName, matchedCategory));
                        
                        promptBuilder.append(String.format(
                            "%s(%s)의 제철을 JSON으로 응답:\n" +
                            "품종명을 전체상품명으로 확장: detectedProductName='%s', seasonalityValue='%s', seasonalityType='%s'\n" +
                            "또한 한국에서 농장체험 가능 여부를 판단하세요. (수입 과일, 열대 과일은 불가, 국내 재배 가능한 작물은 가능)\n" +
                            "{\"detectedProductName\":\"%s\"," +
                            "\"seasonalityType\":\"%s\"," +
                            "\"seasonalityValue\":\"%s\"," +
                            "\"confidence\":0.9," +
                            "\"reasoning\":\"품종명 확장\"," +
                            "\"farmExperienceNote\":\"농장체험 가능 또는 농장체험 불가\"}",
                            productName, category,
                            matchedProductName, matchedValue, matchedType,
                            matchedProductName,
                            matchedType,
                            matchedValue
                        ));
                    } else {
                        // 유사 매칭: 참고만 하고 LLM이 판단
                        // 입력과 결과가 완전히 다른 경우 (예: "오렌지"와 "석류") 오타 교정하지 않음
                        // 참고 정보는 참고만 하고, 입력된 상품명을 우선 사용
                        promptBuilder.append(String.format(
                            "입력 '%s'와 유사한 '%s(%s)' 정보가 있습니다.\n" +
                            "**중요**: 입력된 상품명 '%s'가 참고 정보와 완전히 다른 작물이면, 참고 정보를 무시하고 입력된 상품명 '%s'의 제철을 직접 판단하세요.\n" +
                            "참고 정보는 단지 참고용이며, 입력된 상품명이 정확합니다.\n" +
                            "참고 정보의 상품명이 입력과 의미적으로 유사하거나 포함 관계가 있으면, 참고 정보의 상품명을 우선 사용하세요.\n\n",
                            productName, matchedProductName, matchedCategory, productName, productName));
                        
                        promptBuilder.append(String.format(
                            "%s(%s)의 제철을 JSON으로 응답:\n" +
                            "참고 정보의 상품명이 입력과 의미적으로 유사하면 참고 정보의 상품명을 사용하세요. 그렇지 않으면 입력된 상품명을 그대로 사용하세요.\n" +
                            "또한 한국에서 농장체험 가능 여부를 판단하세요. (수입 과일, 열대 과일은 불가, 국내 재배 가능한 작물은 가능)\n" +
                            "{\"detectedProductName\":\"전체상품명\"," +
                            "\"seasonalityType\":\"MONTH_RANGE|YEAR_ROUND\"," +
                            "\"seasonalityValue\":\"1-3 형식(예:11-2) 또는 연중\"," +
                            "\"confidence\":0.0~1.0," +
                            "\"reasoning\":\"간단설명\"," +
                            "\"farmExperienceNote\":\"농장체험 가능 또는 농장체험 불가\"}",
                            productName, category
                        ));
                    }
                }
        } else {
            log.warn("RAG 검색 결과 없음: productName={}, category={}", productName, category);
        }
        
        // RAG 검색 결과가 없을 때만 일반 프롬프트 사용
        if (knowledgeList == null || knowledgeList.isEmpty()) {
            promptBuilder.append(String.format(
                "%s(%s)의 제철을 JSON으로 응답:\n" +
                "품종명(타이벡,천혜향,설향 등)이면 전체상품명(귤 타이벡,감귤 천혜향,딸기 설향 등)으로 확장\n" +
                "또한 한국에서 농장체험 가능 여부를 판단하세요. (수입 과일, 열대 과일은 불가, 국내 재배 가능한 작물은 가능)\n" +
                "{\"detectedProductName\":\"전체상품명\"," +
                "\"seasonalityType\":\"MONTH_RANGE|YEAR_ROUND\"," +
                "\"seasonalityValue\":\"1-3 형식(예:11-2) 또는 연중\"," +
                "\"confidence\":0.0~1.0," +
                "\"reasoning\":\"간단설명\"," +
                "\"farmExperienceNote\":\"농장체험 가능 또는 농장체험 불가\"}",
                productName, category
            ));
        }
        
        String finalPrompt = promptBuilder.toString();
        log.debug("생성된 프롬프트:\n{}", finalPrompt);
        return finalPrompt;
    }

    /**
     * LLM 응답을 파싱하여 SeasonalityDetectionResponse로 변환
     *
     * @param response LLM 응답 (JSON 형식 또는 마크다운 포함 가능)
     * @param productName 입력받은 상품명 (기본값으로 사용)
     * @return 파싱된 응답
     */
    private SeasonalityDetectionResponse parseLLMResponse(String response, String productName) {
        try {
            // 1. 응답에서 JSON 부분만 추출 (마크다운 코드 블록 제거)
            String jsonText = extractJsonFromResponse(response);
            
            // 2. Jackson으로 JSON 파싱
            JsonNode jsonNode = objectMapper.readTree(jsonText);
            
            // 3. 필드 추출
            String seasonalityTypeStr = jsonNode.get("seasonalityType")
                .asText()
                .toUpperCase()
                .replace("-", "_");
            
            String seasonalityValue = jsonNode.has("seasonalityValue") 
                ? jsonNode.get("seasonalityValue").asText() 
                : "1-12";
            
            // 4. SEASON 타입이면 MONTH_RANGE로 변환 (일관성 확보)
            // 예: "가을" -> "9-11", "봄" -> "3-5"
            SeasonalityType seasonalityType;
            if ("SEASON".equals(seasonalityTypeStr)) {
                seasonalityValue = convertSeasonToMonthRange(seasonalityValue);
                seasonalityType = SeasonalityType.MONTH_RANGE;
                log.debug("SEASON을 MONTH_RANGE로 변환: {} -> {}", seasonalityTypeStr, seasonalityValue);
            } else {
                try {
                    seasonalityType = SeasonalityType.valueOf(seasonalityTypeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("알 수 없는 seasonalityType: {}. MONTH_RANGE로 기본값 사용", seasonalityTypeStr);
                    seasonalityType = SeasonalityType.MONTH_RANGE;
                }
            }
            
            double confidence = jsonNode.has("confidence")
                ? jsonNode.get("confidence").asDouble(0.7)
                : 0.7;
            
            String reasoning = jsonNode.has("reasoning")
                ? jsonNode.get("reasoning").asText("")
                : "";
            
            // LLM이 판단한 농장체험 가능 여부
            String farmExperienceNote = jsonNode.has("farmExperienceNote")
                ? jsonNode.get("farmExperienceNote").asText("농장체험 가능")  // 기본값: 농장체험 가능
                : "농장체험 가능";
            
            // "농장체험 가능" 또는 "농장체험 불가"로 정규화
            if (!farmExperienceNote.equals("농장체험 가능") && !farmExperienceNote.equals("농장체험 불가")) {
                // 다른 형식으로 응답한 경우 판단
                String lowerNote = farmExperienceNote.toLowerCase();
                if (lowerNote.contains("불가") || lowerNote.contains("불가능") || lowerNote.contains("불가능")) {
                    farmExperienceNote = "농장체험 불가";
                } else {
                    farmExperienceNote = "농장체험 가능";
                }
            }
            
            // LLM이 판단한 전체 상품명 추출 (품종명만 입력된 경우 확장된 이름)
            String detectedProductName = jsonNode.has("detectedProductName")
                ? jsonNode.get("detectedProductName").asText(productName)  // 없으면 입력값 사용
                : productName;
            
            log.debug("JSON 파싱 성공: detectedProductName={}, type={}, value={}, confidence={}, farmExperience={}", 
                detectedProductName, seasonalityType, seasonalityValue, confidence, farmExperienceNote);
            
            return new SeasonalityDetectionResponse(
                detectedProductName,
                seasonalityType,
                seasonalityValue,
                confidence,
                reasoning,
                farmExperienceNote
            );
            
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패. LLM 응답: {}", response, e);
            // 파싱 실패는 LLM 응답 형식 오류이므로 예외를 던져서 상위에서 처리
            throw new RuntimeException(
                String.format("LLM 응답 JSON 파싱 실패: %s. 원본 응답: %s", e.getMessage(), response),
                e
            );
        } catch (Exception e) {
            log.error("응답 파싱 중 예외 발생. LLM 응답: {}", response, e);
            // 파싱 중 예외는 심각한 오류이므로 예외를 던져서 상위에서 처리
            throw new RuntimeException(
                String.format("LLM 응답 파싱 중 예외 발생: %s. 원본 응답: %s", e.getMessage(), response),
                e
            );
        }
    }
    
    /**
     * LLM 응답에서 JSON 부분만 추출
     * 마크다운 코드 블록이나 추가 텍스트가 있어도 JSON만 추출
     *
     * @param response 원본 응답 문자열
     * @return 추출된 JSON 문자열
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("응답이 비어있습니다");
        }
        
        // 1. 마크다운 코드 블록 제거 (```json ... ``` 또는 ``` ... ```)
        String cleaned = response.replaceAll("```json\\s*", "")
                                 .replaceAll("```\\s*", "")
                                 .trim();
        
        // 2. JSON 객체 찾기 (중괄호로 시작하고 끝나는 부분)
        Matcher matcher = JSON_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return matcher.group(0);
        }
        
        // 3. JSON 패턴을 찾지 못하면 중괄호로 둘러싸인 첫 번째 부분 사용
        int startIdx = cleaned.indexOf('{');
        int endIdx = cleaned.lastIndexOf('}');
        
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return cleaned.substring(startIdx, endIdx + 1);
        }
        
        // 4. 그래도 찾지 못하면 원본 반환 (파싱 에러 발생 시 예외 처리)
        return cleaned;
    }
    
    /**
     * LLM 응답을 저장할지 여부를 결정
     * 
     * 저장 정책:
     * 1. RAG 검색 결과가 없는 경우: 저장 (새로운 데이터)
     * 2. RAG 검색 결과와 LLM 응답이 동일한 경우: 저장 생략 (이미 데이터베이스에 있음)
     * 3. RAG 검색 결과와 LLM 응답이 다른 경우: 저장 생략 (RAG 데이터가 더 신뢰할 수 있음, 잘못된 정보 저장 방지)
     * 
     * @param llmResponse LLM 응답
     * @param knowledgeList RAG 검색 결과 (null 가능)
     * @return 저장 여부 (true: 저장, false: 생략)
     */
    private boolean shouldStoreLLMResponse(
            SeasonalityDetectionResponse llmResponse,
            List<SeasonalityKnowledgeSearchService.SeasonalityKnowledge> knowledgeList) {
        
        // RAG 검색 결과가 없는 경우 저장 (새로운 데이터)
        if (knowledgeList == null || knowledgeList.isEmpty()) {
            log.debug("RAG 검색 결과 없음 - 새로운 데이터로 저장: {}", 
                llmResponse.detectedProductName());
            return true;
        }
        
        // RAG 검색 결과와 LLM 응답이 동일한지 확인
        SeasonalityKnowledgeSearchService.SeasonalityKnowledge firstMatch = knowledgeList.get(0);
        String matchedProductName = firstMatch.productName();
        String matchedValue = firstMatch.seasonalityValue();
        String matchedType = firstMatch.seasonalityType();
        
        // 상품명, 제철값, 타입이 모두 동일하면 저장 생략
        boolean isSameProduct = llmResponse.detectedProductName().equals(matchedProductName);
        boolean isSameValue = llmResponse.seasonalityValue().equals(matchedValue);
        boolean isSameType = llmResponse.seasonalityType().name().equals(matchedType);
        
        if (isSameProduct && isSameValue && isSameType) {
            log.debug("LLM 응답이 RAG 결과와 완전히 동일 - 저장 생략: {} = {}", 
                llmResponse.detectedProductName(), matchedProductName);
            return false;
        }
        
        // 상품명이 완전히 다른 경우 (예: "두리안" vs "석류") → 새로운 상품이므로 저장해야 함
        // 상품명이 같거나 유사한데 제철값만 다른 경우에만 저장을 막음 (잘못된 정보 방지)
        boolean isDifferentProduct = !isSameProduct && 
            !llmResponse.detectedProductName().contains(matchedProductName) &&
            !matchedProductName.contains(llmResponse.detectedProductName());
        
        if (isDifferentProduct) {
            log.info("새로운 상품으로 판단 - 저장: LLM={}, RAG={} (상품명이 완전히 다름)", 
                llmResponse.detectedProductName(), matchedProductName);
            return true;
        }
        
        // 상품명이 같거나 유사한데 제철값이 다른 경우 → 잘못된 정보일 수 있으므로 저장하지 않음
        // RAG에 이미 있는 데이터가 더 신뢰할 수 있으므로, LLM이 다른 값을 제공하면 저장하지 않음
        log.warn("LLM 응답이 RAG 결과와 다름 - 저장 생략 (잘못된 정보 방지): " +
                "LLM={}({}={}), RAG={}({}={})", 
            llmResponse.detectedProductName(), llmResponse.seasonalityType(), llmResponse.seasonalityValue(),
            matchedProductName, matchedType, matchedValue);
        return false;
    }
    
    /**
     * RAG 검색 결과를 기반으로 confidence 계산
     * 
     * LLM의 주관적 confidence 대신, RAG 검색 결과와의 일치도를 기반으로 
     * 객관적인 confidence 값을 계산합니다.
     * 
     * @param productName 입력 상품명
     * @param category 카테고리
     * @param llmResponse LLM 응답
     * @param knowledgeList RAG 검색 결과 (null 가능)
     * @return 계산된 confidence (0.0 ~ 1.0)
     */
    private double calculateConfidenceBasedOnRAG(
            String productName,
            String category,
            SeasonalityDetectionResponse llmResponse,
            List<SeasonalityKnowledgeSearchService.SeasonalityKnowledge> knowledgeList) {
        
        // RAG가 비활성화되었거나 검색 결과가 없는 경우
        if (knowledgeList == null || knowledgeList.isEmpty()) {
            // LLM confidence 사용 (하지만 신뢰도를 낮춰서)
            double llmConfidence = llmResponse.confidence();
            return Math.min(llmConfidence, 0.65);  // 최대 0.65로 제한
        }
        
        SeasonalityKnowledgeSearchService.SeasonalityKnowledge firstMatch = knowledgeList.get(0);
        String matchedProductName = firstMatch.productName();
        String matchedCategory = firstMatch.category();
        String matchedValue = firstMatch.seasonalityValue();
        
        // 1. RAG 정확한 매칭 → 0.95
        boolean isExactMatch = matchedProductName.equals(productName) && 
                              matchedCategory.equalsIgnoreCase(category);
        if (isExactMatch) {
            log.debug("RAG 정확한 매칭 → confidence: 0.95");
            return 0.95;
        }
        
        // 2. RAG 부분 매칭 (품종명 확장 가능) → 0.75 ~ 0.85
        boolean isContainsMatch = matchedProductName.contains(productName) || 
                                 productName.contains(matchedProductName);
        if (isContainsMatch && matchedCategory.equalsIgnoreCase(category)) {
            // LLM 응답이 RAG 결과와 일치하는지 확인
            if (llmResponse.seasonalityValue().equals(matchedValue)) {
                log.debug("RAG 부분 매칭 + LLM 일치 → confidence: 0.85");
                return 0.85;  // RAG와 LLM이 일치
            } else {
                log.debug("RAG 부분 매칭 + LLM 불일치 → confidence: 0.75");
                return 0.75;  // RAG와 LLM이 불일치 (RAG를 더 신뢰)
            }
        }
        
        // 3. RAG 유사 매칭 → 0.70
        // 유사한 상품이 있지만 정확히 일치하지 않음
        log.debug("RAG 유사 매칭 → confidence: 0.70");
        return 0.70;
    }
    
    /**
     * 계절을 월 범위로 변환
     * 
     * @param season 계절 (봄, 여름, 가을, 겨울)
     * @return 월 범위 문자열 (예: "3-5", "9-11")
     */
    private String convertSeasonToMonthRange(String season) {
        if (season == null) {
            return "1-12";
        }
        
        String trimmed = season.trim();
        return switch (trimmed) {
            case "봄" -> "3-5";
            case "여름" -> "6-8";
            case "가을" -> "9-11";
            case "겨울" -> "12-2";
            default -> {
                log.warn("알 수 없는 계절: {}. 1-12로 기본값 사용", season);
                yield "1-12";
            }
        };
    }
}
