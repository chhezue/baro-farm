package com.barofarm.ai.search.infrastructure.elasticsearch;

import com.barofarm.ai.search.domain.ProductDocument;
import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

// ElasticsearchRepository를 확장해 CRUD/검색 기본 기능 제공
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, UUID> {
}
