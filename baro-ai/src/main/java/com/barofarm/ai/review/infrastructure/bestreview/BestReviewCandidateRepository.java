package com.barofarm.ai.review.infrastructure.bestreview;

import com.barofarm.ai.review.domain.bestreview.BestReviewCandidateDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface BestReviewCandidateRepository
    extends ElasticsearchRepository<BestReviewCandidateDocument, String> {
}
