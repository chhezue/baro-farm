package com.barofarm.ai.season.presentation;

import com.barofarm.ai.common.response.ResponseDto;
import com.barofarm.ai.season.application.SeasonalityDetectionService;
import com.barofarm.ai.season.application.dto.SeasonalityInfo;
import com.barofarm.ai.season.infrastructure.embedding.SeasonalityKnowledgeInitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "제철 판단", description = "Spring AI를 활용한 농산물 제철 판단 API")
@RestController
@RequestMapping("${api.v1}/seasonality")
@RequiredArgsConstructor
public class SeasonalityController {

    private final SeasonalityDetectionService seasonalityService;
    private final SeasonalityKnowledgeInitService knowledgeInitService;

    @Operation(
        summary = "제철 판단 (테스트용)",
        description = "상품명과 카테고리를 입력받아 LLM으로 제철을 판단합니다.\n\n" +
                     "**카테고리 종류**:\n" +
                     "- `FRUIT`: 과일\n" +
                     "- `VEGETABLE`: 채소\n" +
                     "- `ROOT`: 뿌리채소 (고구마, 감자 등)\n" +
                     "- `GRAIN`: 곡류 (쌀, 보리 등)\n" +
                     "- `NUT`: 견과류\n" +
                     "- `MUSHROOM`: 버섯류\n" +
                     "- `ETC`: 그 외"
    )
    @GetMapping("/detect")
    public ResponseDto<SeasonalityInfo> detectSeasonality(
        @Parameter(
            description = "상품명", 
            example = "딸기", 
            required = true
        )
        @RequestParam String productName,
        
        @Parameter(
            description = "카테고리 (상품 분류)", 
            example = "FRUIT",
            required = true,
            schema = @Schema(
                type = "string",
                allowableValues = {
                    "FRUIT",      // 과일
                    "VEGETABLE",  // 채소
                    "ROOT",       // 뿌리채소
                    "GRAIN",      // 곡류
                    "NUT",        // 견과류
                    "MUSHROOM",   // 버섯류
                    "ETC"         // 그 외
                }
            )
        )
        @RequestParam String category
    ) {
        SeasonalityInfo result = seasonalityService.detectSeasonality(productName, category);
        return ResponseDto.ok(result);
    }

    @Operation(
        summary = "제철 지식 데이터 초기화",
        description = "CSV 파일에서 제철 지식 데이터를 읽어서 VectorStore에 임베딩합니다.\n\n" +
                     "**변경 사항**:\n" +
                     "- 기존: 애플리케이션 시작 시 자동 초기화\n" +
                     "- 변경: API 호출을 통한 수동 초기화\n\n" +
                     "**주의사항**:\n" +
                     "- CSV 파일 경로: `seasonality.csv.path` 설정값 사용\n" +
                     "- 중복 호출 시 VectorStore에 중복 데이터가 추가될 수 있습니다.\n" +
                     "- ElasticsearchVectorStore 사용 시 영구 저장됩니다."
    )
    @PostMapping("/init")
    public ResponseDto<InitResponse> initializeKnowledge() {
        int loadedCount = knowledgeInitService.initializeFromCsv();
        return ResponseDto.ok(new InitResponse(loadedCount, "제철 지식 데이터 초기화 완료"));
    }

    /**
     * 초기화 응답 DTO
     */
    public record InitResponse(
        int loadedCount,
        String message
    ) {}
}

