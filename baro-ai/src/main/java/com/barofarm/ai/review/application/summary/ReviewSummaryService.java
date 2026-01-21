package com.barofarm.ai.review.application.summary;

import com.barofarm.ai.event.model.ReviewSummaryEvent;
import com.barofarm.ai.review.domain.review.Sentiment;
import com.barofarm.ai.review.domain.summary.ReviewSummaryDocument;
import com.barofarm.ai.review.infrastructure.kafka.ReviewSummaryEventProducer;
import com.barofarm.ai.review.infrastructure.summary.ReviewSummaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

    private final ChatClient chatClient;
    private final ReviewSummaryRepository summaryRepository;
    private final ReviewSummaryEventProducer eventProducer;

    public void summarizeFromContents(String productId, Sentiment sentiment, List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }

        String summary = requestSummary(productId, sentiment, contents);
        if (!StringUtils.hasText(summary)) {
            return;
        }

        ReviewSummaryDocument document = new ReviewSummaryDocument(
            productId,
            sentiment,
            summary.trim(),
            LocalDateTime.now()
        );
        summaryRepository.save(document);
        UUID productUuid = parseProductId(productId);
        if (productUuid != null) {
            eventProducer.send(new ReviewSummaryEvent(
                productUuid,
                sentiment.name(),
                document.getSummaryText(),
                document.getUpdatedAt()
            ));
        }
    }

    private String requestSummary(String productId, Sentiment sentiment, List<String> contents) {
        String prompt = buildPrompt(productId, sentiment, contents);
        try {
            return chatClient.prompt()
                .user(u -> u.text(prompt))
                .call()
                .entity(String.class);
        } catch (Exception e) {
            log.warn("요약에 실패했습니다. productId={} sentiment={}", productId, sentiment, e);
            return "";
        }
    }

    private String buildPrompt(String productId, Sentiment sentiment, List<String> contents) {
        String joined = contents.stream()
            .map(c -> "- " + c.replace("\n", " ").trim())
            .collect(Collectors.joining("\n"));

        return """
        너는 쇼핑몰 상품 리뷰를 요약하는 역할이다.

        아래 리뷰들은 특정 상품(ProductId)의 리뷰 중,
        "%s" 감정(POSITIVE 또는 NEGATIVE)에 해당하는 리뷰들이다.

        작업 목표:
        - 리뷰들을 하나하나 다시 쓰지 말고,
          여러 리뷰에서 반복적으로 나타나는 핵심 특징만 요약하라.
        - 이 상품의 장점/단점을 처음 보는 사람도 바로 이해할 수 있도록 작성하라.

        요약 기준:
        - 최대 3줄까지만 작성한다.
        - 의미 있는 내용이 부족하면 1~2줄만 작성해도 된다.
        - 각 줄은 한 문장으로, 짧고 명확하게 작성한다.
        - "리뷰에 따르면", "사용자들은" 같은 표현은 사용하지 않는다.
        - 감정(%s)에 맞는 내용만 포함한다.

        ProductId: %s

        리뷰 목록:
        %s

        출력 형식:
        - 반드시 한국어로 작성한다.
        - 요약 문장 1
        - 요약 문장 2
        - 요약 문장 3
        """.formatted(
            sentiment.name(),
            sentiment.name(),
            productId,
            joined
        );
    }

    private java.util.UUID parseProductId(String productId) {
        try {
            return java.util.UUID.fromString(productId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid productId for summary event. productId={}", productId, e);
            return null;
        }
    }
}
