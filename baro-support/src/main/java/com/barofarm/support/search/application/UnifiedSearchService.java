package com.barofarm.support.search.application;

import com.barofarm.support.common.response.CustomPage;
import com.barofarm.support.search.application.dto.ExperienceAutoItem;
import com.barofarm.support.search.application.dto.ExperienceSearchItem;
import com.barofarm.support.search.application.dto.ProductAutoItem;
import com.barofarm.support.search.application.dto.ProductSearchItem;
import com.barofarm.support.search.application.dto.UnifiedAutoCompleteResponse;
import com.barofarm.support.search.application.dto.UnifiedSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnifiedSearchService {
    private final ProductSearchService productSearchService;
    private final ExperienceSearchService experienceSearchService;

    // 통합 검색
    public UnifiedSearchResponse search(String q, Pageable pageable) {
        // 각 서비스에서 CustomPage를 직접 반환받아서 조합
        CustomPage<ProductSearchItem> products = productSearchService.searchProducts(q, pageable);
        CustomPage<ExperienceSearchItem> experiences = experienceSearchService.searchExperiences(q, pageable);

        return new UnifiedSearchResponse(products, experiences);
    }

    // 통합 자동완성
    public UnifiedAutoCompleteResponse autocomplete(String q) {
        List<ProductAutoItem> autoProducts = productSearchService.autocomplete(q);
        List<ExperienceAutoItem> autoExperiences = experienceSearchService.autocomplete(q);

        return new UnifiedAutoCompleteResponse(autoProducts, autoExperiences); // 최대 15개 반환됨.
    }
}
