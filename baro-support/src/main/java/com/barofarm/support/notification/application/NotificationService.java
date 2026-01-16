package com.barofarm.support.notification.application;

import com.barofarm.support.notification.domain.Notification;
import com.barofarm.support.notification.domain.NotificationIdGenerator;
import com.barofarm.support.notification.domain.NotificationRepository;
import com.barofarm.support.notification.domain.NotificationType;
import com.barofarm.support.notification.infrastructure.sse.SseEmitterRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * [Application Service]
 * - 유스케이스를 orchestration 합니다.
 * - Domain 규칙 + Infra 기술을 조합하는 계층
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

        // 1) 도메인 객체 생성(규칙 체크)
        Notification notification = Notification.create(
            idGenerator.generate(),
            userId,
            type,
            title,
            content,
            relatedUrl,
            now
        );

        // 2) DB 저장(영속화)
        Notification saved = repository.save(notification);

        // 3) SSE 연결이 존재하면 “추가로” 실시간 전송
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
     * SSE 구독(연결)을 만들어서 저장
     * - 이후 server에서 이벤트 발생하면 emitter.send로 push 가능
     */
    public SseEmitter subscribe(String userId) {
        // 1시간 유지 (예시)
        SseEmitter emitter = new SseEmitter(60L * 60 * 1000);

        sseRepo.save(userId, emitter);

        // 연결 종료/오류/타임아웃 시 메모리 정리
        emitter.onCompletion(() -> sseRepo.remove(userId));
        emitter.onTimeout(() -> sseRepo.remove(userId));
        emitter.onError(e -> sseRepo.remove(userId));

        // 최초 connect 이벤트 (클라이언트가 연결 성공 확인용)
        safeSend(emitter, "connected");

        return emitter;
    }

    private void safeSend(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name("notification")
                .data(data));
        } catch (Exception e) {
            // 전송 실패는 연결이 끊긴 것일 가능성이 높으므로 제거
            // (멀티 인스턴스면 여기 로직만으로는 완전 보장 불가)
        }
    }
}
