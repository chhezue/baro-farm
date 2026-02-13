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
     * SSE 援щ룆 ?붾뱶?ъ씤??
     * - 釉뚮씪?곗??먯꽌??EventSource濡??곌껐
     * - Spring??SseEmitter??SSE 援ы쁽 ?꾧뎄?낅땲?? :contentReference[oaicite:6]{index=6}
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-User-Id") String userId) {
        return service.subscribe(userId);
    }

    /**
     * (?뚯뒪?몄슜) ?뚮┝ 媛뺤젣 ?앹꽦 API
     * - ?ㅼ젣 ?댁쁺?먯꽌??Kafka consumer媛 createAndDispatch瑜??몄텧?섎뒗 ?뺥깭媛 留롮뒿?덈떎.
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
