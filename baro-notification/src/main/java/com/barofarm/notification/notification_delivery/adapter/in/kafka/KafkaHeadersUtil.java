package com.barofarm.notification.notification_delivery.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka Header 조회 유틸.
 * 헤더 키를 안전하게 조회하고 문자열로 변환한다.
 */
public final class KafkaHeadersUtil {

    private KafkaHeadersUtil() {}

    public static Optional<String> getStringHeader(Headers headers, String key) {
        if (headers == null || key == null) {
            return Optional.empty();
        }
        var header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }

    public static String getStringHeaderOrNull(Headers headers, String key) {
        return getStringHeader(headers, key).orElse(null);
    }
}

