package com.barofarm.ai.season.infrastructure.repository;

import com.barofarm.ai.season.domain.SeasonalityDetectionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 제철 판단 로그 Repository
 */
@Repository
public interface SeasonalityDetectionLogRepository extends JpaRepository<SeasonalityDetectionLog, UUID> {

    /**
     * 특정 상품의 제철 판단 로그 조회 (최신순)
     *
     * @param productId 상품 ID
     * @return 제철 판단 로그 목록
     */
    List<SeasonalityDetectionLog> findByProductIdOrderByCreatedAtDesc(UUID productId);

    /**
     * 특정 상태의 제철 판단 로그 조회
     *
     * @param status 상태 (SUCCESS, FAILED, SKIPPED)
     * @return 제철 판단 로그 목록
     */
    List<SeasonalityDetectionLog> findByStatusOrderByCreatedAtDesc(SeasonalityDetectionLog.DetectionStatus status);
}


