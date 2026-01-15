package com.barofarm.ai.review.domain;

import com.barofarm.ai.event.model.ReviewEvent;
import com.barofarm.ai.event.model.ReviewEvent.ReviewEventData;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Getter
@Document(indexName = "review_event")
@NoArgsConstructor
public class ReviewDocument {

    @Id
    private String id; // reviewId.toString()

    private String productId;

    private Integer rating;

    private String content;

    private Sentiment sentiment;

    private LocalDateTime occurredAt;

    public ReviewDocument(String id, String productId, Integer rating, String content, Sentiment sentiment,
                          LocalDateTime occurredAt) {
        this.id = id;
        this.productId = productId;
        this.rating = rating;
        this.content = content;
        this.sentiment = sentiment;
        this.occurredAt = occurredAt;
    }

    public static ReviewDocument from(ReviewEvent event, Sentiment sentiment) {
        ReviewEventData data = event.data();

        return new ReviewDocument(
            data.reviewId().toString(),
            data.productId().toString(),
            data.rating(),
            data.content(),
            sentiment,
            data.occurredAt()
        );
    }
}
