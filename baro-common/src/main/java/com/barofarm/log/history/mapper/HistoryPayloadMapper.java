package com.barofarm.log.history.mapper;

import com.barofarm.log.history.model.HistoryEventType;
import java.util.Map;

public interface HistoryPayloadMapper {
    HistoryEventType supports();

    Map<String, Object> payload(Object[] args, Object returnValue);

    default boolean mapBeforeProceed() {
        return false;
    }
}
