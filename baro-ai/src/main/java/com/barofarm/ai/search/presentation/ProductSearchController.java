package com.barofarm.ai.search.presentation;

import com.barofarm.ai.search.application.ProductSearchService;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSearchResponse;
import com.barofarm.dto.CustomPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품 검색", description = "상품 검색 및 자동완성 API")
@RestController
@RequestMapping("${api.v1}/search/product")
@RequiredArgsConstructor
public class ProductSearchController {

    private final ProductSearchService service;

    @Operation(summary = "상품 검색", description = "키워드로 상품을 검색 (상품만)")
    @GetMapping
    // 프론트는 Query Parameter로 보내고, 백엔드는 @ModelAttribute로 묶어서 받음.
    public CustomPage<ProductSearchResponse> searchProducts(
        // UUID는 선택 사항: 존재하는 경우에만 사용자 행동 로그를 남긴다.
        @RequestHeader(value = "X-User-Id", required = false) UUID userId,
        @Parameter(description = "검색 조건 DTO") @ModelAttribute ProductSearchRequest request,
        @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.searchOnlyProducts(userId, request, pageable);
    }

    @Operation(summary = "상품 자동완성", description = "키워드로 상품명 자동완성 (상품만)")
    @GetMapping("/autocomplete")
    public List<ProductAutoCompleteResponse> autocomplete(
        @Parameter(description = "자동완성 키워드", example = "사") @RequestParam String query,
        @Parameter(description = "자동완성 값 개수") @RequestParam(required = false, defaultValue = "5") int size
    ) {
        // 자동완성 호출은 "상품 검색 행동" 로그 대상이 아닌 것으로 간주 (간단히 유지)
        return service.autocomplete(query, size);
    }
}
