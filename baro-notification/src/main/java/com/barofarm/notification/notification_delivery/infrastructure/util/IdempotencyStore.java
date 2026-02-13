package com.barofarm.notification.notification_delivery.infrastructure.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IdempotencyStore
 *
 * 紐⑹쟻:
 * - ?숈씪??eventId瑜?以묐났 泥섎━(以묐났 諛쒖넚)?섏? ?딅룄濡?留됰뒗??
 *
 * ??援ы쁽? "In-memory" 踰꾩쟾?대씪:
 * - ?쒕쾭 ?ъ떆?묓븯硫?湲곕줉???좎븘媛?
 * - ?몄뒪?댁뒪媛 ?щ윭 媛쒕㈃ 媛곸옄 ?곕줈 湲곗뼲??(?꾨꼍?섏? ?딆쓬)
 *
 * ?댁쁺?먯꽌 硫???몄뒪?댁뒪?쇰㈃ Redis/DB 湲곕컲?쇰줈 諛붽씀??寃??뺤꽍?대떎.
 */
public class IdempotencyStore {

    private final Map<String, Instant> processed = new ConcurrentHashMap<>();
    private final Duration ttl;

    public IdempotencyStore(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * @return true硫?"泥섎━?????놁쓬 ??吏湲?泥섎━ 媛??
     *         false硫?"?대? 泥섎━????以묐났?대?濡??ㅽ궢"
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
