package com.barofarm.ai.season.infrastructure.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 파일에서 제철 지식 데이터를 로드하여 VectorStore에 임베딩
 * 
 * S3 마운트 경로: /mnt/s3/dataset/season/seasonality-data.csv
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seasonality.rag.enabled", havingValue = "true", matchIfMissing = false)
public class SeasonalityKnowledgeLoader {
    
    private final VectorStore vectorStore;
    private final String csvFilePath;
    
    public SeasonalityKnowledgeLoader(
            @Qualifier("seasonalityVectorStore") VectorStore vectorStore,
            @Value("${seasonality.csv.path:/mnt/s3/dataset/season/seasonality-data.csv}") String csvFilePath) {
        this.vectorStore = vectorStore;
        this.csvFilePath = csvFilePath;
    }
    
    /**
     * CSV 파일을 읽어서 VectorStore에 임베딩
     * 
     * @return 로드된 문서 수
     */
    public int loadFromCsv() {
        log.info("제철 지식 CSV 파일 로드 시작: {}", csvFilePath);
        
        Path path = Paths.get(csvFilePath);
        if (!Files.exists(path)) {
            log.error("CSV 파일을 찾을 수 없습니다: {}", csvFilePath);
            log.error("현재 작업 디렉토리: {}", System.getProperty("user.dir"));
            log.error("절대 경로 시도: {}", path.toAbsolutePath());
            return 0;
        }
        
        try {
            long fileSize = Files.size(path);
            log.info("CSV 파일 발견: {} (크기: {} bytes)", csvFilePath, fileSize);
        } catch (IOException e) {
            log.warn("CSV 파일 크기 확인 실패: {}", csvFilePath, e);
        }
        
        List<Document> documents = new ArrayList<>();
        int lineCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            // 헤더 스킵
            String header = reader.readLine();
            if (header == null) {
                log.warn("CSV 파일이 비어있습니다: {}", csvFilePath);
                return 0;
            }
            
            log.debug("CSV 헤더: {}", header);
            
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                try {
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 6) {
                        log.warn("CSV 라인 {} 파싱 실패 (필드 수 부족): {}", lineCount, line);
                        continue;
                    }
                    
                    String productName = fields[0].trim();
                    String category = fields[1].trim();
                    String content = fields[2].trim();
                    String seasonalityType = fields[3].trim();
                    String seasonalityValue = fields[4].trim();
                    String sourceType = fields[5].trim();
                    
                    Document document = SeasonalityKnowledgeDocument.createDocument(
                        productName, category, content,
                        seasonalityType, seasonalityValue, sourceType
                    );
                    
                    documents.add(document);
                    
                } catch (Exception e) {
                    log.warn("CSV 라인 {} 파싱 실패: {}", lineCount, line, e);
                }
            }
            
            // VectorStore에 일괄 추가
            if (!documents.isEmpty()) {
                log.info("VectorStore에 {}개 문서 임베딩 중...", documents.size());
                vectorStore.add(documents);
                log.info("제철 지식 데이터 로드 완료: {}개 문서", documents.size());
            } else {
                log.warn("로드할 문서가 없습니다.");
            }
            
            return documents.size();
            
        } catch (IOException e) {
            log.error("CSV 파일 읽기 실패: {}", csvFilePath, e);
            return 0;
        }
    }
    
    /**
     * CSV 라인 파싱 (쉼표 구분, 따옴표 처리)
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // 마지막 필드 추가
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
}

