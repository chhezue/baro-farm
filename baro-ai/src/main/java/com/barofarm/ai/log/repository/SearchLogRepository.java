package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
}
