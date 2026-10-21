package com.barofarm.ai.search.presentation;

import com.barofarm.ai.search.application.ProductReindexService;
import com.barofarm.ai.search.application.ProductReindexService.ReindexPageResult;
import com.barofarm.ai.search.exception.SearchErrorCode;
import com.barofarm.dto.ResponseDto;
import com.barofarm.exception.CustomException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 전체 재색인을 사람이 직접 돌릴 때 쓰는 관리 API입니다. 평소 색인은 Kafka 이벤트로 이뤄집니다.
 *
 * <p><b>토큰이 있어야 동작합니다.</b> baro-ai는 인증을 직접 하지 않고 게이트웨이가 넣어 주는 헤더를
 * 신뢰하는 구조인데, 이 경로는 부를 때마다 상품 수만큼 외부 AI 호출이 나가서 아무나 부를 수 있으면
 * 비용을 그대로 태울 수 있습니다. 그래서 환경 변수로 넣은 토큰과 맞을 때만 받습니다.
 * <b>토큰을 설정하지 않으면 이 경로는 항상 거절합니다</b> — 열어 두는 쪽이 기본값이 되지 않게 하기 위해서입니다.
 *
 * <p>한 번에 한 페이지씩 처리하므로 부르는 쪽이 `hasNext`가 false가 될 때까지 페이지를 올리며 반복합니다.
 * 외부 AI 한도에 맞춰 사이에 간격을 두는 것도 부르는 쪽 몫입니다.
 */
@Tag(name = "검색 관리", description = "상품 재색인 등 운영 작업")
@RestController
@RequestMapping("${api.v1}/search/admin")
@RequiredArgsConstructor
public class ProductReindexController {

    /** 관리 작업을 허용할 토큰. 비어 있으면 이 경로를 쓸 수 없다 */
    @Value("${baro.ai.admin-token:}")
    private String adminToken;

    private final ProductReindexService productReindexService;

    /**
     * 상품 한 페이지를 원본에서 다시 읽어 색인합니다.
     *
     * 매핑을 바꾼 뒤라면 <b>사람이 먼저 인덱스를 지우고 다시 만든 다음</b> 이 API를 부릅니다 —
     * 되돌릴 수 없는 삭제는 API 뒤에 두지 않았습니다. 인덱스가 그대로인 상태에서 불러도 안전합니다.
     * 상품 ID로 덮어쓰므로 같은 페이지를 두 번 처리해도 결과가 같습니다.
     */
    @Operation(summary = "상품 재색인 (한 페이지)")
    @PostMapping("/reindex")
    public ResponseDto<ReindexPageResult> reindex(
        @RequestHeader(value = "X-Admin-Token", required = false) String token,
        @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "이 페이지에서 읽을 상품 수 (1~100)", example = "20")
        @RequestParam(defaultValue = "20") int size
    ) {
        verifyAdminToken(token);
        return ResponseDto.ok(productReindexService.reindexPage(page, size));
    }

    /**
     * 설정된 토큰과 맞는지 확인합니다. 토큰을 설정하지 않은 서버에서는 무조건 거절합니다.
     */
    private void verifyAdminToken(String token) {
        if (adminToken == null || adminToken.isBlank() || !adminToken.equals(token)) {
            throw new CustomException(SearchErrorCode.ADMIN_TOKEN_INVALID);
        }
    }
}
