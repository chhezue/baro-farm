package com.barofarm.notification.notification.presentation;

import com.barofarm.notification.notification.application.NotificationService;
import com.barofarm.notification.notification.domain.Notification;
import com.barofarm.notification.notification.domain.NotificationType;
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
     * SSE 구독 엔드포인트.
     * 클라이언트(EventSource) 연결을 유지하며 이벤트를 푸시한다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-User-Id") String userId) {
        return service.subscribe(userId);
    }

    /**
     * 테스트용 알림 생성 API.
     * 운영 환경에서는 이벤트 소비 로직에서 호출하는 경로를 사용한다.
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
