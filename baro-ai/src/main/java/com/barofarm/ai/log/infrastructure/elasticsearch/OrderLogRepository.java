package com.barofarm.ai.log.infrastructure.elasticsearch;

import com.barofarm.ai.log.domain.OrderLogDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface OrderLogRepository extends ElasticsearchRepository<OrderLogDocument, String> {
    List<OrderLogDocument> findAllByUserIdAndOccurredAtAfter(UUID userId, Instant after);

    // 각 타입별로 최대 5개씩 최근 데이터를 가져옴 (시간 내림차순)
    List<OrderLogDocument> findAllByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(
        UUID userId, Instant after, Pageable pageable);
}
