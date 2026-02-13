package com.barofarm.notification.notification_delivery.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka Header ?쎄린 ?좏떥
 *
 * ???꾩슂?쒓??
 * - ?ㅻТ?먯꽌??payload JSON留뚰겮?대굹 header??以묒슂???뺣낫媛 ?ㅼ뼱媛꾨떎.
 *   ?? eventId, traceId, producer ?쒕퉬?ㅻ챸, schemaVersion ??
 *
 * ?덉떆 ?ㅻ뜑 洹쒖빟(異붿쿇):
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
