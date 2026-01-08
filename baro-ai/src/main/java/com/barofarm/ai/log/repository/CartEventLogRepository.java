package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.CartEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartEventLogRepository extends JpaRepository<CartEventLog, Long> {
}
