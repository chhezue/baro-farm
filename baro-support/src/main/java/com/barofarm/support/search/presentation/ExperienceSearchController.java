package com.barofarm.support.search.presentation;

import com.barofarm.support.common.response.CustomPage;
import com.barofarm.support.search.application.ExperienceSearchService;
import com.barofarm.support.search.application.dto.ExperienceAutoItem;
import com.barofarm.support.search.application.dto.ExperienceSearchItem;
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

@Tag(name = "체험 검색", description = "체험 검색 및 자동완성 API")
@RestController
@RequestMapping("${api.v1}/search/experience")
@RequiredArgsConstructor
public class ExperienceSearchController {

    private final ExperienceSearchService service;

    @Operation(summary = "체험 검색", description = "키워드로 체험을 검색합니다.")
    @GetMapping
    public CustomPage<ExperienceSearchItem> searchExperiences(
        @Parameter(description = "검색 키워드", example = "농장체험") @RequestParam(required = false) String keyword,
        @Parameter(description = "페이지 정보") Pageable pageable) {
        return service.searchExperiences(keyword, pageable);
    }

    @Operation(summary = "체험 자동완성", description = "키워드로 체험명을 자동완성합니다.")
    @GetMapping("/autocomplete")
    public List<ExperienceAutoItem> autocomplete(
        @Parameter(description = "자동완성 키워드", example = "농") @RequestParam String query) {
        return service.autocomplete(query);
    }
}
