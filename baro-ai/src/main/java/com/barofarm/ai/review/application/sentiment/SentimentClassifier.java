package com.barofarm.ai.review.application.sentiment;

import com.barofarm.ai.review.domain.Sentiment;

public interface SentimentClassifier {
    Sentiment classify(Integer rating, String content);
}
