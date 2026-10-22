package com.barofarm.ai.recommend.application;

import com.barofarm.ai.recommend.exception.RecommendErrorCode;
import com.barofarm.ai.search.application.VectorProductSearchService;
import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.exception.CustomException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기준 벡터를 받아 유사 상품 또는 개인화 추천 목록을 만드는 공통 파이프라인입니다.
 *
 * <p>"유사 상품 추천"(기준 상품 벡터)과 "개인화 추천"(사용자 취향 벡터)은 기준 벡터를 얻는 출처만 다르고
 * 이후 처리(후보 조회 → 고르기 → DTO 변환)가 같아 여기로 모았습니다.
 *
 * <p><b>고르는 방식이 2026-09-09에 바뀌었습니다.</b> 그전에는 후보 중 서로 가장 멀리 떨어진 것들을
 * 반복해서 뽑는 방식(Greedy Max-Min)이었는데, 그 알고리즘은 첫 번째를 고른 뒤부터
 * <b>기준 벡터를 전혀 보지 않습니다.</b> 정의상 "후보 중 서로 가장 안 닮은 것"을 고르므로, 유사 상품을
 * 찾아 달라는 요청에 가장 덜 유사한 조합을 돌려주고 있었습니다. 그러면서 후보를 3배 더 받아야 했고,
 * 후보마다 768차원 벡터를 함께 실어 와야 했으며(문서당 약 10KB), 그 전송량 때문에 임베딩 차원을 줄이는
 * 작업까지 따라왔습니다 — 알고리즘 하나가 후속 결정 여러 개를 만들어 낸 셈입니다.
 *
 * <p>지금은 <b>유사도 순서를 그대로 두고 같은 카테고리만 상한을 겁니다.</b> 원래 막고 싶었던 것이
 * "추천 5개가 사실상 같은 상품"이었고, 그건 카테고리 상한으로 직접 막을 수 있습니다. 벡터 간 거리를
 * 계산할 필요가 없어져 후보에서 벡터를 받아 오지 않아도 되고, 코드도 200줄 줄었습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRecommendPipeline {

    /**
     * 요청 가능한 추천 개수의 상한.
     *
     * 2026-09-09 이전에는 이 값이 33이었습니다. 내부 후보 상한(100)을 다양화 배수(3)로 나눈 값이라
     * 구현 사정이 그대로 공개 계약으로 새어 나간 숫자였습니다 — 클라이언트가 "왜 33인가"를 알 방법이
     * 없었습니다. 후보를 더 받아도 벡터를 싣지 않게 되면서 그 제약이 사라져, 설명 가능한 값으로 바꿨습니다.
     */
    private static final int MAX_TOP_K = 50;

    /**
     * 최종 개수(topK) 대비 몇 배를 후보로 받을지.
     *
     * 카테고리 상한에 걸려 건너뛰는 후보가 생기므로 여유가 필요합니다. 3배면 한 카테고리가 후보를
     * 독점하는 최악의 경우에도 상한을 지키면서 topK를 채울 수 있습니다.
     */
    private static final int CANDIDATE_MULTIPLIER = 3;

    /**
     * 한 카테고리에서 고를 수 있는 최대 개수.
     *
     * 2로 두면 기본값인 5개 추천에서 최소 3개 카테고리가 나옵니다. 같은 카테고리 쌍의 비율로 보면
     * 5개 중 최대 20%(2+2+1 구성일 때 10쌍 중 2쌍)이고, 이 값은 계산으로 나오는 상한이라 매번 같습니다.
     * 예전 Max-Min 방식은 실측 11.5%로 이보다 낮았지만, 그 대신 유사도 순서를 버렸습니다 —
     * 카테고리를 덜 겹치게 하려고 덜 비슷한 상품을 추천하고 있었다는 뜻입니다.
     * 근거: docs/audits/full-module/001-complexity-and-overengineering.md O3
     */
    private static final int MAX_PER_CATEGORY = 2;

    /** 카테고리 코드가 비어 있는 상품을 한 묶음으로 세기 위한 키 */
    private static final String UNCATEGORIZED = "";

    /** Elasticsearch `dense_vector`를 `script_score`로 훑어 코사인 유사도 검색을 수행하는 서비스 */
    private final VectorProductSearchService vectorProductSearchService;

    /**
     * 추천 개수(topK)가 허용 범위(1~50) 안인지 확인하고, 벗어나면 400으로 끊습니다.
     *
     * 추천 API는 로그인 없이도 호출되므로 주소창에 임의의 숫자를 넣을 수 있습니다. 검증을 두지 않으면
     * 음수·0은 조용히 빈 목록으로, 아주 큰 값은 곱셈이 넘쳐 역시 빈 목록으로 나가 원인을 알 수 없습니다.
     * 조회를 시작하기 전에 부르므로, 프로필이나 기준 상품이 없어 일찍 반환되는 경우에도 같은 400이 나갑니다.
     */
    public static void validateTopK(int topK) {
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new CustomException(RecommendErrorCode.INVALID_TOP_K);
        }
    }

    /**
     * 기준 벡터와 유사도가 높은 상품을 topK개 고릅니다. 같은 카테고리는 최대 {@value #MAX_PER_CATEGORY}개까지만 담습니다.
     *
     * @param queryVector       추천의 기준이 되는 768차원 벡터 (상품 벡터 또는 사용자 취향 벡터)
     * @param excludeProductIds 추천 결과에서 제외할 상품 (예: 현재 보고 있는 상품, 이미 산 상품)
     * @param topK              최종 사용자에게 노출할 추천 상품 개수
     * @return 추천 상품 응답 DTO 목록 (후보가 없으면 빈 목록)
     */
    public List<ProductSummaryResponse> recommend(
        float[] queryVector,
        List<UUID> excludeProductIds,
        int topK
    ) {
        // [1단계: 후보 조회] 카테고리 상한에 걸려 건너뛸 것을 감안해 넉넉히 받는다.
        List<ProductDocument> candidates = vectorProductSearchService.findSimilarProductDocumentsByVector(
            queryVector, topK * CANDIDATE_MULTIPLIER, excludeProductIds);

        if (candidates.isEmpty()) {
            return List.of();
        }

        // [2단계: 유사도 순서를 지키면서 카테고리 쏠림만 막는다]
        return selectWithCategorySpread(candidates, topK).stream()
            .map(ProductSummaryResponse::from)
            .toList();
    }

    /**
     * 유사도 순으로 훑되 같은 카테고리를 {@value #MAX_PER_CATEGORY}개까지만 담고, 그래도 모자라면
     * 남은 후보를 순서대로 채웁니다.
     *
     * 두 번 훑는 이유는 <b>요청한 개수보다 적게 돌려주지 않기 위해서</b>입니다. 카테고리가 한두 개뿐인
     * 좁은 카탈로그에서는 상한만 지키면 topK를 못 채우는데, 그때는 "덜 다양한 5개"가 "다양한 2개"보다 낫습니다.
     * 상한은 쏠림을 막자는 것이지 결과를 줄이자는 것이 아닙니다.
     *
     * <p>후보는 이미 Elasticsearch가 유사도 순으로 정렬해 돌려준 것이라 여기서 다시 정렬하지 않습니다.
     * 테스트에서 직접 부를 수 있게 패키지 접근입니다 — 인스턴스 상태를 쓰지 않습니다.
     */
    static List<ProductDocument> selectWithCategorySpread(List<ProductDocument> candidates, int topK) {
        List<ProductDocument> selected = new ArrayList<>(topK);
        boolean[] picked = new boolean[candidates.size()];
        Map<String, Integer> perCategory = new HashMap<>();

        // 1차: 카테고리 상한을 지키면서 유사도 순으로 담는다
        for (int i = 0; i < candidates.size() && selected.size() < topK; i++) {
            ProductDocument candidate = candidates.get(i);
            String category = categoryKeyOf(candidate);
            if (perCategory.getOrDefault(category, 0) >= MAX_PER_CATEGORY) {
                continue;
            }
            perCategory.merge(category, 1, Integer::sum);
            picked[i] = true;
            selected.add(candidate);
        }

        // 2차: 상한 때문에 모자라면 남은 것을 순서대로 채운다
        for (int i = 0; i < candidates.size() && selected.size() < topK; i++) {
            if (!picked[i]) {
                selected.add(candidates.get(i));
            }
        }

        return selected;
    }

    private static String categoryKeyOf(ProductDocument product) {
        String code = product.getProductCategoryCode();
        return code == null || code.isBlank() ? UNCATEGORIZED : code;
    }
}
