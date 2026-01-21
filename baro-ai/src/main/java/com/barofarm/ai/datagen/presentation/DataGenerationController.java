package com.barofarm.ai.datagen.presentation;

import com.barofarm.ai.datagen.application.DataGenerationService;
import com.barofarm.ai.datagen.application.UserLogGenerationService;
import com.barofarm.ai.datagen.application.dto.AutoAmplifyResponse;
import com.barofarm.ai.embedding.application.UserProfileEmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "데이터 생성 도구", description = "개발 및 테스트 편의를 위한 API")
@RestController
@RequestMapping("/api/v1/datagen")
@RequiredArgsConstructor
public class DataGenerationController {

    private final DataGenerationService dataGenerationService;
    private final UserLogGenerationService userLogGenerationService;
    private final UserProfileEmbeddingService userProfileEmbeddingService;

    @Operation(summary = "자동 상품 데이터 증폭", description = "SQL 파일을 기반으로 LLM을 사용하여 상품 데이터를 500개로 자동 증폭하고 SQL 파일로 저장합니다.")
    @PostMapping("/auto-amplify-products")
    public AutoAmplifyResponse autoAmplifyProducts(
            @Parameter(description = "SQL 파일 경로 (기본값: scripts/generate-dummy/product_dummy_origin.sql)")
            @RequestParam(defaultValue = "scripts/generate-dummy/product_dummy_origin.sql") String sqlFilePath)
            throws IOException {
        return dataGenerationService.autoAmplifyProducts(sqlFilePath);
    }

    @Operation(
        summary = "사용자 행동 로그 생성",
        description = "하드코딩된 테스트 사용자에 대한 더미 행동 로그(검색/장바구니/주문)를 생성합니다. "
            + "Kafka 이벤트가 아직 머지되지 않아 실제 이벤트를 받을 수 없을 때 사용합니다. "
            + "각 타입별로 최대 5개씩 생성되며, UserProfileEmbeddingService가 사용하는 형식에 맞춰 생성됩니다. "
            + "검색 로그는 LLM으로 생성된 키워드를 사용하며, 장바구니/주문 로그는 Elasticsearch에서 랜덤으로 가져온 상품을 사용합니다. "
            + "로그는 계속 쌓이며, UserProfileEmbeddingService는 최신순으로 각 타입별 최대 5개씩만 사용합니다. "
            + "⚠️ 주의: 로그 생성 후 반드시 프로필 임베딩을 재생성해야 추천 결과가 변경됩니다. "
            + "(POST /api/v1/datagen/user-profile-embedding 호출)"
    )
    @PostMapping("/dummy-logs")
    public ResponseEntity<String> generateDummyLogs() {
        try {
            userLogGenerationService.generateDummyLogsForUser();
            return ResponseEntity.ok(
                "더미 로그 생성 완료 - 하드코딩된 테스트 사용자에 대한 로그가 생성되었습니다.\n" +
                "⚠️ 중요: 추천 결과를 업데이트하려면 다음 API를 호출하세요:\n" +
                "POST /api/v1/datagen/user-profile-embedding"
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("더미 로그 생성 실패: " + e.getMessage());
        }
    }

    @Operation(
        summary = "사용자 프로필 임베딩 생성",
        description = "하드코딩된 테스트 사용자의 행동 로그를 기반으로 프로필 임베딩 벡터를 생성합니다. "
            + "최근 30일간의 로그(검색/장바구니/주문 각 최대 5개씩)를 사용하여 1536차원 벡터를 생성합니다."
    )
    @PostMapping("/user-profile-embedding")
    public ResponseEntity<String> generateUserProfileEmbedding() {
        UUID testUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        try {
            userProfileEmbeddingService.updateUserProfileEmbedding(testUserId);
            return ResponseEntity.ok(
                String.format("사용자 프로필 임베딩 생성 완료 - User: %s", testUserId)
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("사용자 프로필 임베딩 생성 실패: " + e.getMessage());
        }
    }
}
