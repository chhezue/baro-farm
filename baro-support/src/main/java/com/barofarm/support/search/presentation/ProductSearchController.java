package com.barofarm.support.search.presentation;

import com.barofarm.support.common.response.CustomPage;
import com.barofarm.support.search.application.ProductSearchService;
import com.barofarm.support.search.application.dto.ProductAutoItem;
import com.barofarm.support.search.application.dto.ProductSearchItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품 검색", description = "상품 검색 및 자동완성 API")
@RestController
@RequestMapping("${api.v1}/search/product")
@RequiredArgsConstructor
public class ProductSearchController {

    private final ProductSearchService service;

    @Operation(summary = "상품 검색", description = "키워드로 상품을 검색합니다.")
    @GetMapping
    public CustomPage<ProductSearchItem> searchProducts(
        @Parameter(description = "검색 키워드", example = "사과") @RequestParam(required = false) String keyword,
        @Parameter(description = "페이지 정보") Pageable pageable) {
        return service.searchProducts(keyword, pageable);
    }

    @Operation(summary = "상품 자동완성", description = "키워드로 상품명을 자동완성합니다.")
    @GetMapping("/autocomplete")
    public List<ProductAutoItem> autocomplete(
        @Parameter(description = "자동완성 키워드", example = "사") @RequestParam String query) {
        return service.autocomplete(query);
    }
}
