package com.barofarm.ai.embedding.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Builder
@Document(indexName = "user_profile_embeddings")
public class UserProfileEmbeddingDocument {

    // 사용자 ID를 문서의 고유 ID로 사용
    @Id
    private UUID userId;

    // OpenAI 'text-embedding-ada-002' 모델의 기본 차원인 1536으로 설정
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private List<Double> userProfileVector;

    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;
}
