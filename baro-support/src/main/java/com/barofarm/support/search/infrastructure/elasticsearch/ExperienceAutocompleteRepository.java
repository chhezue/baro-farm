package com.barofarm.support.search.infrastructure.elasticsearch;

import com.barofarm.support.search.domain.ExperienceAutocompleteDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ExperienceAutocompleteRepository
    extends ElasticsearchRepository<ExperienceAutocompleteDocument, UUID> {

    // "토마"가 입력되면 experienceName이 "토마"로 시작하고 status가 ON_SALE인 문서만 반환
    @Query("""
        {
          "bool": {
            "must": {
              "match_phrase_prefix": {
                "experienceName": {
                  "query": "?0",
                  "max_expansions": 10
                }
              }
            },
            "filter": {
              "term": {
                "status": "ON_SALE"
              }
            }
          },
          "size": 3
        }
        """)
    List<ExperienceAutocompleteDocument> findByPrefix(String prefix);
}
