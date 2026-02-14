package com.barofarm.notification.notification.infrastructure.sse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRepository {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void save(String userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return;
        }
        emitters.put(userId, emitter);
    }

    public Optional<SseEmitter> findByUserId(String userId) {
        return Optional.ofNullable(emitters.get(userId));
    }

    public void remove(String userId) {
        if (userId == null) {
            return;
        }
        emitters.remove(userId);
    }
}
