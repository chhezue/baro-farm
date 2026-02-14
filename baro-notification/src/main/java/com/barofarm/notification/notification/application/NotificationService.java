package com.barofarm.notification.notification.application;

import com.barofarm.notification.notification.domain.Notification;
import com.barofarm.notification.notification.domain.NotificationIdGenerator;
import com.barofarm.notification.notification.domain.NotificationRepository;
import com.barofarm.notification.notification.domain.NotificationType;
import com.barofarm.notification.notification.infrastructure.sse.SseEmitterRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림 생성/조회/읽음 처리와 SSE 전송을 오케스트레이션하는 애플리케이션 서비스.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final SseEmitterRepository sseRepo;
    private final NotificationIdGenerator idGenerator;

    @Transactional
    public Notification createAndDispatch(
        String userId,
        NotificationType type,
        String title,
        String content,
        String relatedUrl
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        Notification notification = Notification.create(
            idGenerator.generate(),
            userId,
            type,
            title,
            content,
            relatedUrl,
            now
        );

        Notification saved = repository.save(notification);

        sseRepo.findByUserId(userId).ifPresent(emitter -> safeSend(emitter, saved));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyRecent(String userId) {
        return repository.findRecentByUserId(userId, 50);
    }

    @Transactional(readOnly = true)
    public long countMyUnread(String userId) {
        return repository.countUnreadByUserId(userId, OffsetDateTime.now());
    }

    @Transactional
    public int markAllAsRead(String userId) {
        return repository.markAllUnreadAsRead(userId, OffsetDateTime.now());
    }

    /**
     * SSE 구독 연결을 생성하고 저장한다.
     * 연결 종료/오류/타임아웃 시 저장소에서 정리한다.
     */
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(60L * 60 * 1000);

        sseRepo.save(userId, emitter);

        emitter.onCompletion(() -> sseRepo.remove(userId));
        emitter.onTimeout(() -> sseRepo.remove(userId));
        emitter.onError(e -> sseRepo.remove(userId));

        safeSend(emitter, "connected");

        return emitter;
    }

    private void safeSend(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name("notification")
                .data(data));
        } catch (Exception e) {
        }
    }
}
