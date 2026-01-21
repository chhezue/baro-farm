package com.barofarm.support.notification.presentation;

import com.barofarm.support.notification.application.NotificationService;
import com.barofarm.support.notification.domain.Notification;
import com.barofarm.support.notification.domain.NotificationType;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public List<Notification> myNotifications(@RequestHeader("X-User-Id") String userId) {
        return service.getMyRecent(userId);
    }

    @GetMapping("/unread/count")
    public long unreadCount(@RequestHeader("X-User-Id") String userId) {
        return service.countMyUnread(userId);
    }

    @PutMapping("/read-all")
    public int readAll(@RequestHeader("X-User-Id") String userId) {
        return service.markAllAsRead(userId);
    }

    /**
     * SSE 구독 엔드포인트
     * - 브라우저에서는 EventSource로 연결
     * - Spring의 SseEmitter는 SSE 구현 도구입니다. :contentReference[oaicite:6]{index=6}
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-User-Id") String userId) {
        return service.subscribe(userId);
    }

    /**
     * (테스트용) 알림 강제 생성 API
     * - 실제 운영에서는 Kafka consumer가 createAndDispatch를 호출하는 형태가 많습니다.
     */
    @PostMapping("/test")
    public Notification createTest(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam NotificationType type,
        @RequestParam String title
    ) {
        return service.createAndDispatch(userId, type, title, "content", "/orders/123");
    }
}
