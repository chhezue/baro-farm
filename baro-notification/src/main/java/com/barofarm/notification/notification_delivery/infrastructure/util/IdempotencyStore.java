package com.barofarm.notification.notification_delivery.infrastructure.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * IdempotencyStore
 *
 * 목적:
 * - 동일한 eventId를 중복 처리(중복 발송)하지 않도록 막는다.
 *
 * 이 구현은 "In-memory" 버전이라:
 * - 서버 재시작하면 기록이 날아감
 * - 인스턴스가 여러 개면 각자 따로 기억함 (완벽하지 않음)
 *
 * 운영에서 멀티 인스턴스라면 Redis/DB 기반으로 바꾸는 게 정석이다.
 *
 * eventId 기준 중복 처리를 막는 인메모리 멱등 저장소.
 * 멀티 인스턴스 환경에서는 Redis/DB 기반 구현으로 교체해야 한다.
 */
public class IdempotencyStore {

    private final Map<String, Instant> processed = new ConcurrentHashMap<>();
    private final Duration ttl;

    public IdempotencyStore(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * @return true면 아직 처리되지 않은 이벤트, false면 이미 처리된 이벤트
     */
    public boolean tryMarkProcessed(String eventId) {
        cleanupExpired();

        Instant now = Instant.now();
        Instant existing = processed.putIfAbsent(eventId, now);
        return existing == null;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        for (var entry : processed.entrySet()) {
            Instant time = entry.getValue();
            if (time.plus(ttl).isBefore(now)) {
                processed.remove(entry.getKey());
            }
        }
    }
}
