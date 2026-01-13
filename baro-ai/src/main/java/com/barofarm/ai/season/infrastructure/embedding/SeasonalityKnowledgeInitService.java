package com.barofarm.ai.season.infrastructure.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 제철 지식 데이터 초기화 서비스
 * 
 * **변경 사항**: 애플리케이션 시작 시 자동 초기화 제거
 * - 기존: CommandLineRunner로 앱 시작 시 자동 실행
 * - 변경: API 호출을 통한 수동 초기화로 전환
 * 
 * 초기화는 `/api/v1/seasonality/init` API를 통해 수동으로 실행됩니다.
 * 
 * 활성화: seasonality.rag.enabled=true
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seasonality.rag.enabled", havingValue = "true", matchIfMissing = false)
public class SeasonalityKnowledgeInitService {
    
    private final SeasonalityKnowledgeLoader knowledgeLoader;
    
    public SeasonalityKnowledgeInitService(SeasonalityKnowledgeLoader knowledgeLoader) {
        this.knowledgeLoader = knowledgeLoader;
    }
    
    /**
     * CSV 데이터를 VectorStore에 로드 (API 호출 시 사용)
     * 
     * @return 로드된 문서 수
     */
    public int initializeFromCsv() {
        log.info("제철 지식 데이터 초기화 시작 (API 호출)");
        
        try {
            int loadedCount = knowledgeLoader.loadFromCsv();
            log.info("제철 지식 데이터 초기화 완료: {}개 문서 로드", loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("제철 지식 데이터 초기화 실패", e);
            throw new RuntimeException("제철 지식 데이터 초기화 실패", e);
        }
    }
}


