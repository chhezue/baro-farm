package com.barofarm.ai.event.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceEvent {
    private ExperienceEventType type;
    private ExperienceEventData data;

    public enum ExperienceEventType {
        EXPERIENCE_CREATED,
        EXPERIENCE_UPDATED,
        EXPERIENCE_DELETED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceEventData {

        // 체험 상세/검색/추천 전체에서 공통으로 사용하는 식별자:
        // - 유저의 검색/예약/주문 로그와 조인할 때 기본 키로 사용
        private UUID experienceId;

        // 체험이 어떤 농장/판매자에 속하는지 구분:
        // - 특정 농장 체험만 자주 보는 유저의 패턴을 분석해
        //   해당 농장/비슷한 타입의 체험을 개인화 추천할 때 활용
        private UUID farmId;

        // 사용자가 실제로 인지하는 체험 이름:
        // - 임베딩 입력 텍스트의 핵심 (title + description 조합)
        // - 유사 체험 추천 시 제목 유사도 기반 후보 필터링에 활용
        private String title;

        // 체험 설명:
        // - title과 함께 콘텐츠 기반 임베딩의 주요 소스
        // - "자연/동물/아이동반" 등 키워드를 통해 유사 컨셉 체험 추천 가능
        private String description;

        // 1인당 가격:
        // - 사용자별 "체험 가격대 선호"를 추정하는 데 사용
        // - 개인화 추천 시, 유저가 자주 보는/예약하는 가격 구간의 체험을 우선 랭킹
        private Long pricePerPerson;

        // 수용 인원:
        // - 검색/예약 시 자주 사용하는 capacity 필터를 통해
        //   "혼자/커플/가족/단체" 성향을 파악하고,
        //   비슷한 규모의 체험을 개인화 추천할 수 있음
        private Integer capacity;

        // 체험 소요 시간(분):
        // - "짧은 체험 vs 반나절/종일 코스" 선호를 분석
        // - 유저가 많이 조회/예약한 duration 범위 내에서 후보군을 좁힐 때 사용
        private Integer durationMinutes;

        // 예약 가능 시작/종료일:
        // - 계절성/시즌성 분석(예: 여름 체험, 주말 위주 예약)에 사용 가능
        // - 향후 "다가오는 주말에 갈 수 있는 맞춤 체험" 같은 추천 시 필터로 사용
        private LocalDateTime availableStartDate;
        private LocalDateTime availableEndDate;

        // 체험 상태:
        // - ACTIVE/INACTIVE 등 노출 가능 여부 판단
        // - 개인화 추천 결과에서 예약 불가 상태의 체험을 걸러내는 필터
        private String status;

        // 최신 스냅샷 시점:
        // - updatedAt 이후 변경된 체험만 임베딩 재계산/캐시 재구성하는 기준
        // - 로그/추천 결과와 도메인 데이터의 시점 일관성을 맞출 때 활용
        private Instant updatedAt;
    }
}
