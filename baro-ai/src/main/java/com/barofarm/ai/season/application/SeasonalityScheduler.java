package com.barofarm.ai.season.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 제철 정보 판단을 위한 주기적 스케줄러
 * Spring Batch 없이 가벼운 @Scheduled 방식 사용
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeasonalityScheduler {

    private final SeasonalityDetectionService detectionService;

    /**
     * 매일 새벽 2시에 실행
     * 제철 정보가 없는 기존 상품들의 제철을 판단
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void detectSeasonalityForExistingProducts() {
        log.info("기존 상품 제철 판단 주기 작업 시작");

        try {
            // TODO: buyer-service에서 제철 정보가 없는 상품 목록 조회
            // List<ProductSummary> products = productClient.getProductsWithoutSeasonality();

            // TODO: 페이징 처리로 상품 목록 조회 및 처리
            // 예시:
            // int pageSize = 50;
            // int offset = 0;
            // while (true) {
            //     List<ProductSummary> products = 
            //         productClient.getProductsWithoutSeasonality(offset, pageSize);
            //     
            //     if (products.isEmpty()) {
            //         break;
            //     }
            //     
            //     products.forEach(product -> 
            //         detectionService.detectSeasonalityAsync(
            //             product.id(),
            //             product.name(),
            //             product.category()
            //         )
            //     );
            //     
            //     Thread.sleep(1000);  // Rate Limit 방지
            //     offset += pageSize;
            //     
            //     if (offset >= 500) {  // 하루 최대 500개
            //         break;
            //     }
            // }

            log.info("기존 상품 제철 판단 주기 작업 완료");

        } catch (Exception e) {
            log.error("기존 상품 제철 판단 주기 작업 실패", e);
        }
    }

    /**
     * 주 1회 실행 (일요일 새벽 3시)
     * 제철 정보가 있는 상품의 재검증 (선택사항)
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void revalidateSeasonality() {
        log.info("제철 정보 재검증 주기 작업 시작");
        // TODO: 구현 필요 (선택사항)
    }
}
