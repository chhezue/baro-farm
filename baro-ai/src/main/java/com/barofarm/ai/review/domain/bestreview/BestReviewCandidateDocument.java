package com.barofarm.ai.review.domain.bestreview;

import com.barofarm.ai.review.domain.review.Sentiment;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Document(indexName = "product_best_review_index")
@NoArgsConstructor
public class BestReviewCandidateDocument {

    @Id
    private String id; // productId + "_" + sentiment code

    @Field(type = FieldType.Keyword)
    private String productId;

    @Field(type = FieldType.Keyword)
    private Sentiment sentiment;

    @Field(type = FieldType.Keyword)
    private List<String> reviewIds;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;

    public BestReviewCandidateDocument(String productId, Sentiment sentiment, List<String> reviewIds,
                                       LocalDateTime updatedAt) {
        this.id = buildId(productId, sentiment);
        this.productId = productId;
        this.sentiment = sentiment;
        this.reviewIds = reviewIds;
        this.updatedAt = updatedAt;
    }

    public static String buildId(String productId, Sentiment sentiment) {
        return productId + "_" + sentiment.code();
    }
}
