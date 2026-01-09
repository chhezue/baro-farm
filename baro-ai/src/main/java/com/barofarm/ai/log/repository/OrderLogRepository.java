package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.OrderLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface OrderLogRepository extends ElasticsearchRepository<OrderLogDocument, String> {
}
