package com.barofarm.ai.search.infrastructure.elasticsearch;

import com.barofarm.ai.search.domain.ExperienceDocument;
import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

// ElasticsearchRepository를 확장해 CRUD/검색 기본 기능 제공
public interface ExperienceSearchRepository
    extends ElasticsearchRepository<ExperienceDocument, UUID> {
}
