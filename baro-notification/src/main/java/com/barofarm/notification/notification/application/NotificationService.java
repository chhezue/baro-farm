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
 * [Application Service]
 * - ?좎뒪耳?댁뒪瑜?orchestration ?⑸땲??
 * - Domain 洹쒖튃 + Infra 湲곗닠??議고빀?섎뒗 怨꾩링
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

        // 1) ?꾨찓??媛앹껜 ?앹꽦(洹쒖튃 泥댄겕)
        Notification notification = Notification.create(
            idGenerator.generate(),
            userId,
            type,
            title,
            content,
            relatedUrl,
            now
        );

        // 2) DB ????곸냽??
        Notification saved = repository.save(notification);

        // 3) SSE ?곌껐??議댁옱?섎㈃ ?쒖텛媛濡쒋??ㅼ떆媛??꾩넚
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
     * SSE 援щ룆(?곌껐)??留뚮뱾?댁꽌 ???
     * - ?댄썑 server?먯꽌 ?대깽??諛쒖깮?섎㈃ emitter.send濡?push 媛??
     */
    public SseEmitter subscribe(String userId) {
        // 1?쒓컙 ?좎? (?덉떆)
        SseEmitter emitter = new SseEmitter(60L * 60 * 1000);

        sseRepo.save(userId, emitter);

        // ?곌껐 醫낅즺/?ㅻ쪟/??꾩븘????硫붾え由??뺣━
        emitter.onCompletion(() -> sseRepo.remove(userId));
        emitter.onTimeout(() -> sseRepo.remove(userId));
        emitter.onError(e -> sseRepo.remove(userId));

        // 理쒖큹 connect ?대깽??(?대씪?댁뼵?멸? ?곌껐 ?깃났 ?뺤씤??
        safeSend(emitter, "connected");

        return emitter;
    }

    private void safeSend(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name("notification")
                .data(data));
        } catch (Exception e) {
            // ?꾩넚 ?ㅽ뙣???곌껐???딄릿 寃껋씪 媛?μ꽦???믪쑝誘濡??쒓굅
            // (硫???몄뒪?댁뒪硫??ш린 濡쒖쭅留뚯쑝濡쒕뒗 ?꾩쟾 蹂댁옣 遺덇?)
        }
    }
}
