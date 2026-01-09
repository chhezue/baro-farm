package com.barofarm.ai.event.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchEvent {

    private SearchEventData data;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchEventData {

        // "로그인한 사용자" 기준 개인화만 지원:
        // - userId 없는 검색(게스트)은 이벤트로 쌓지 않거나, 별도 통계용으로만 다룸(미구현)
        private UUID userId;

        // userId별 검색 취향을 표현하는 핵심 텍스트:
        // - 여러 검색어를 시간 순으로 이어 붙여 임베딩하면, "이 유저가 자주 찾는 주제"를 표현하는 벡터가 됨
        private String searchQuery;

        // 상품 검색 시 선택한 카테고리 필터:
        // - 검색어와 별개로, "항상 특정 카테고리로 필터링"하는 습관을 개인별로 파악
        private String category;

//        // 정렬 기준/방향:
//        // - "가격순 정렬을 자주 쓰는 유저"는 가격 민감도가 높은 유저로 해석 가능
//        private String sortBy;
//        private String sortDirection;
//
//        // 가격 필터:
//        // - userId별로 minPrice/maxPrice의 사용 패턴을 보면,
//        //   기대 가격 범위를 유추해 개인화 추천에 반영 가능
//        private Integer minPrice;
//        private Integer maxPrice;
//
//        // 이 검색이 "풍부한 결과"를 가져왔는지 여부:
//        // - resultCount가 항상 작으면, 이 유저의 니즈에 맞는 상품이 부족하다는 신호
//        //   (개인화보다는 상품 기획 영역이지만, 후속 추천 전략을 달리할 수 있음)
//        private Integer resultCount;

        // 검색 시각:
        // - userId별 시간대/요일 패턴 분석,
        //   "밤에 주로 검색하는 재료/체험" vs "주간 검색"을 구분할 때 사용
        private Instant searchedAt;
    }
}
