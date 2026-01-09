package com.barofarm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "history.log")
public class HistoryLogProperties {
    private String aiTopic = "ai-history";

    private String userIdHeader = "X-User-Id";

    public String getAiTopic() {
        return aiTopic;
    }

    public void setAiTopic(String aiTopic) {
        this.aiTopic = aiTopic;
    }

    public String getUserIdHeader() {
        return userIdHeader;
    }

    public void setUserIdHeader(String userIdHeader) {
        this.userIdHeader = userIdHeader;
    }
}
