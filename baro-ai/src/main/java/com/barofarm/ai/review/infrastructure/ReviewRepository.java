package com.barofarm.ai.review.infrastructure;

import com.barofarm.ai.review.domain.ReviewDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ReviewRepository extends ElasticsearchRepository<ReviewDocument, String> {
}
