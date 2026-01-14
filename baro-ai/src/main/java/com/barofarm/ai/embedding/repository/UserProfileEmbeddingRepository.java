package com.barofarm.ai.embedding.repository;

import com.barofarm.ai.embedding.model.UserProfileEmbeddingDocument;
import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserProfileEmbeddingRepository extends ElasticsearchRepository<UserProfileEmbeddingDocument, UUID> {
}
