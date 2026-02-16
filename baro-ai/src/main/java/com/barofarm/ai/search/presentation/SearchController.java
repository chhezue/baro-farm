package com.barofarm.ai.search.presentation;

import com.barofarm.ai.search.application.ProductIndexService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검색", description = "상품 검색 및 자동완성")
@RestController
@RequestMapping("${api.v1}/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchService productSearchService;
    private final ProductIndexService productIndexService;

    @Operation(summary = "검색", description = "키워드로 상품 검색. 카테고리/가격 필터 선택 가능.")
    @GetMapping
    public ResponseDto<CustomPage<ProductSearchResponse>> search(
        @RequestHeader(value = "X-User-Id", required = false) UUID userId,
        @Parameter(description = "검색어", example = "토마토", required = false) @RequestParam(required = false) String q,
        @Parameter(description = "카테고리 (복수 가능)") @RequestParam(required = false) List<String> categories,
        @Parameter(description = "최소 가격") @RequestParam(required = false) Long priceMin,
        @Parameter(description = "최대 가격") @RequestParam(required = false) Long priceMax,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        ProductSearchRequest request = new ProductSearchRequest(q, categories, priceMin, priceMax);
        return ResponseDto.ok(productSearchService.search(userId, request, pageable));
    }

    @Operation(summary = "자동완성", description = "키워드로 상품 자동완성")
    @GetMapping("/autocomplete")
    public ResponseDto<List<ProductAutoCompleteResponse>> autocomplete(
        @Parameter(description = "자동완성 검색어", example = "토마", required = true) @RequestParam String q,
        @Parameter(description = "자동완성 값 개수") @RequestParam(required = false, defaultValue = "5") int size
    ) {
        return ResponseDto.ok(productIndexService.autocomplete(q, size));
    }
}
