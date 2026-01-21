package com.barofarm.ai.embedding.infrastructure.elasticsearch;

import com.barofarm.ai.embedding.domain.UserProfileEmbeddingDocument;
import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserProfileEmbeddingRepository extends ElasticsearchRepository<UserProfileEmbeddingDocument, UUID> {
}
