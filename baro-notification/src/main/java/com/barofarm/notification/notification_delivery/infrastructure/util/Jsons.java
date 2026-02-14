package com.barofarm.notification.notification_delivery.infrastructure.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kafka 메시지 JSON 직렬화/역직렬화를 담당하는 유틸 클래스.
 * Consumer 쪽에서 허용 범위를 통일하기 위해 ObjectMapper 설정을 고정한다.
 */
public class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Jsons() {}

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON parsing failed: " + e.getMessage(), e);
        }
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON serialization failed: " + e.getMessage(), e);
        }
    }
}

