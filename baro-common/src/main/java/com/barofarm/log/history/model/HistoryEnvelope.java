package com.barofarm.log.history.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoryEnvelope<T>(
    HistoryEventType type,
    OffsetDateTime time,
    UUID userId,
    T data) {
}
