package com.barofarm.ai.event.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchEvent {
    private SearchEventType type;
    private SearchEventData data;

    public enum SearchEventType {
        SEARCH_PERFORMED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchEventData {
        private UUID userId;
        private String searchQuery;
        private String category;
        private Instant searchedAt;
    }
}
