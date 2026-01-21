package com.barofarm.support.notification_delivery.infrastructure.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * [JSON 직렬화/역직렬화 유틸]
 *
 * 필요성 :
 * - Kafka payload를 STRING으로 받으면 Consumer에서 DTO로 파싱
 * - ObjectMapper 설정(unknown 필드 무시 등)을 한 곳에서 통일하기 위해서
 *
 * 주의:
 * - "Producer(notification)"와 "Consumer(notification_delivery)"가
 *   payload 버전이 약간 달라질 수 있어서
 *   unknown field 무시 설정을 켜는 것이 매우 중요하다.
 * */
public class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        // payload에 필드가 늘어나도 consumer가 죽지 않도록 안전하게.
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
