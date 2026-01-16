package com.barofarm.support.notification_delivery.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka Header 읽기 유틸
 *
 * 왜 필요한가?
 * - 실무에서는 payload JSON만큼이나 header에 중요한 정보가 들어간다.
 *   예) eventId, traceId, producer 서비스명, schemaVersion 등
 *
 * 예시 헤더 규약(추천):
 * - X-Event-Id: UUID
 * - X-Trace-Id: distributed tracing
 * - X-Schema-Version: 1
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
