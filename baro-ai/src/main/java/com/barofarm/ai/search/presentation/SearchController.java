package com.barofarm.ai.search.presentation;

import com.barofarm.ai.search.application.ProductSearchService;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSearchResponse;
import com.barofarm.dto.CustomPage;
import com.barofarm.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 검색 및 실시간 검색어 자동완성 API를 제공하는 웹 계층 컨트롤러입니다.
 *
 * [제공 엔드포인트]
 * 1. 상품 키워드 검색: `GET /api/v1/search`
 *    - 검색 대상 텍스트는 상품명(`productName`) 하나이고, 카테고리·가격은 매칭이 아니라 필터로만 쓰입니다.
 *      상품 문서에 설명 필드는 없습니다. 페이징을 지원합니다.
 *    - 로그인 사용자의 경우 취향 벡터 갱신을 위한 검색 로그가 함께 기록됩니다.
 *      로그 적재는 검색 응답 시간에 포함되는 동기 작업이고, 취향 벡터 재계산만 별도 스레드로 넘어갑니다.
 * 2. 실시간 검색어 자동완성: `GET /api/v1/search/autocomplete`
 *    - 사용자가 검색창에 키워드를 타이핑할 때 Edge N-gram 역색인을 통해 연관 상품명을 최대 size건 추천.
 */
@Tag(name = "검색", description = "상품 검색 및 자동완성")
@RestController
@RequestMapping("${api.v1}/search")
@RequiredArgsConstructor
public class SearchController {

    /** Elasticsearch 상품 키워드 검색 및 자동완성을 처리하는 애플리케이션 서비스 */
    private final ProductSearchService productSearchService;

    /**
     * 키워드, 카테고리, 가격 범위를 조건으로 상품 목록을 페이지네이션 검색합니다.
     *
     * @param userId     요청 헤더(X-User-Id)에서 추출한 로그인 사용자 UUID (비회원 검색 시 null 가능)
     * @param q          사용자 입력 검색어 (비어있을 경우 전체 상품 목록 조회)
     * @param categories 필터링할 상품 카테고리 목록 (선택)
     * @param priceMin   최소 상품 가격 필터 (원)
     * @param priceMax   최대 상품 가격 필터 (원)
     * @param page       페이지 번호 (0부터 시작, 기본값: 0)
     * @param size       페이지 당 출력 상품 개수 (기본값: 10, 최대 100)
     * @return 검색된 상품 목록 및 페이징 정보가 담긴 CustomPage DTO
     */
    @Operation(summary = "검색", description = "키워드로 상품 검색. 카테고리/가격 필터 선택 가능.")
    @GetMapping
    public ResponseDto<CustomPage<ProductSearchResponse>> search(
        @RequestHeader(value = "X-User-Id", required = false) UUID userId,
        @Parameter(description = "검색어", example = "토마토", required = false) @RequestParam(required = false) String q,
        @Parameter(description = "카테고리 (복수 가능)") @RequestParam(required = false) List<String> categories,
        @Parameter(description = "최소 가격") @RequestParam(required = false) Long priceMin,
        @Parameter(description = "최대 가격") @RequestParam(required = false) Long priceMax,
        @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "10") int size
    ) {
        // [1단계: 요청 DTO 조립]
        // 쿼리 파라미터들을 캡슐화한 ProductSearchRequest 객체 생성
        ProductSearchRequest request = new ProductSearchRequest(q, categories, priceMin, priceMax);

        // [2단계: 검색 서비스 호출 및 응답 반환]
        // Elasticsearch 역색인 검색 수행 및 비동기 검색 행동 로그 적재
        return ResponseDto.ok(productSearchService.search(userId, request, page, size));
    }

    /**
     * 입력 중인 검색어 접두어를 바탕으로 연관된 상품명 목록을 실시간으로 자동완성 추천합니다.
     *
     * @param q    입력 중인 검색어 접두어 (예: "토마")
     * @param size 추천받을 최대 자동완성 키워드 개수 (기본값: 5, 최대 20)
     * @return 자동완성 상품명 응답 DTO 목록
     */
    @Operation(summary = "자동완성", description = "키워드로 상품 자동완성")
    @GetMapping("/autocomplete")
    public ResponseDto<List<ProductAutoCompleteResponse>> autocomplete(
        @Parameter(description = "자동완성 검색어", example = "토마", required = true) @RequestParam String q,
        @Parameter(description = "자동완성 값 개수 (1~20)") @RequestParam(required = false, defaultValue = "5") int size
    ) {
        // [서비스 위임 및 응답 반환]
        // Edge N-gram 인덱스를 조회하여 접두어와 일치하는 상품명 추출 후 반환
        return ResponseDto.ok(productSearchService.autocomplete(q, size));
    }
}
