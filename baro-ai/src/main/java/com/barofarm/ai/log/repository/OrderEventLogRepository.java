package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.OrderEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventLogRepository extends JpaRepository<OrderEventLog, Long> {
}
