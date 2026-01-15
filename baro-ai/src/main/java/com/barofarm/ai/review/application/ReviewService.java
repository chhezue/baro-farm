package com.barofarm.ai.review.application;

import com.barofarm.ai.event.model.ReviewEvent;
import com.barofarm.ai.review.domain.ReviewDocument;
import com.barofarm.ai.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewDocument saveReview(ReviewEvent reviewEvent) {
        return null;
    }

    public ReviewDocument updateReview(ReviewEvent reviewEvent) {
        return null;
    }

    public ReviewDocument deleteReview(ReviewEvent reviewEvent) {
        return null;
    }
}
