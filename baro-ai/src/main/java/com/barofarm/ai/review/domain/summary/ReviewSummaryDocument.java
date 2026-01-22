package com.barofarm.ai.review.domain.summary;

import com.barofarm.ai.review.domain.review.Sentiment;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Document(indexName = "product_review_summary")
@NoArgsConstructor
public class ReviewSummaryDocument {

    @Id
    private String id; // productId + "_" + sentiment code

    @Field(type = FieldType.Keyword)
    private String productId;

    @Field(type = FieldType.Keyword)
    private Sentiment sentiment;

    @Field(type = FieldType.Text)
    private String summaryText;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;

    public ReviewSummaryDocument(String productId, Sentiment sentiment, String summaryText, LocalDateTime updatedAt) {
        this.id = buildId(productId, sentiment);
        this.productId = productId;
        this.sentiment = sentiment;
        this.summaryText = summaryText;
        this.updatedAt = updatedAt;
    }

    public static String buildId(String productId, Sentiment sentiment) {
        return productId + "_" + sentiment.code();
    }
}
