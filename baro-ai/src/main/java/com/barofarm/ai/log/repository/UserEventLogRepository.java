package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.UserEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventLogRepository extends JpaRepository<UserEventLog, Long> {
}
