package com.barofarm.ai.log.repository;

import com.barofarm.ai.log.domain.SearchLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SearchLogRepository extends ElasticsearchRepository<SearchLogDocument, String> {
}
