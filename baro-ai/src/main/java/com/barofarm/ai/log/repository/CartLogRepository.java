package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.CartLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CartLogRepository extends ElasticsearchRepository<CartLogDocument, String> {
}
