package com.barofarm.support.notification_delivery.application.service;

import com.barofarm.support.notification_delivery.application.port.EmailSenderPort;
import com.barofarm.support.notification_delivery.application.port.PushSenderPort;
import com.barofarm.support.notification_delivery.application.port.RecipientResolverPort;
import com.barofarm.support.notification_delivery.domain.event.NotificationEventPayload;
import com.barofarm.support.notification_delivery.domain.model.DeliveryChannel;
import com.barofarm.support.notification_delivery.domain.model.RecipientProfile;
import com.barofarm.support.notification_delivery.infrastructure.util.IdempotencyStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 알림 "전송 유스케이스"의 핵심 서비스.
 *
 * [기본 설명]
 * - Producer(notification 도메인)는 인앱 저장을 담당한다.
 * - DeliveryService는 외부 채널 발송만 담당한다.
 *
 * */

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final RecipientResolverPort recipientResolver;
    private final EmailSenderPort emailSender;
    private final PushSenderPort pushSender;
    private final IdempotencyStore idempotencyStore;

    public void deliver(NotificationEventPayload payload) {

        if (!idempotencyStore.tryMarkProcessed(payload.eventId())) {
            // 이미 처리한 이벤트면 그냥 종료 (중복 방지)
            return;
        }

        // 1) 수신자 프로필 조회 (이메일 주소, FCM 토큰 등)
        RecipientProfile profile = recipientResolver.resolve(payload.recipientUserId());

        // 2) 어떤 채널로 발송할지를 결정
        Set<DeliveryChannel> channels = parseChannels(payload.channels());

        // 3) EMAIL 발송
        if (channels.contains(DeliveryChannel.EMAIL)) {
            // email 없으면 발송 불가 → 실패로 처리할지, 스킵할지 정책 선택
            if (profile.email() == null || profile.email().isBlank()) {
                throw new IllegalStateException("Recipient email missing");
            }
            emailSender.send(profile.email(), payload.title(), payload.body());
        }

        // 4) PUSH 발송
        if (channels.contains(DeliveryChannel.PUSH)) {
            // FCM 토큰 없으면 push 불가
            if (profile.fcmToken() == null || profile.fcmToken().isBlank()) {
                throw new IllegalStateException("Recipient fcmToken missing");
            }
            pushSender.send(profile.fcmToken(), payload.title(), payload.body(), payload.deepLink());
        }

        // 5) 성공 처리
        // (필요하면 delivery 로그 테이블을 따로 만들 수 있음)
    }

    private Set<DeliveryChannel> parseChannels(List<String> raw) {
        // 요청 채널 미지정 시 발송 생략 (Default: None)
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<DeliveryChannel> channels = new HashSet<>();
        for (String ch : raw) {
            if (ch == null) {
                continue;
            }
            try {
                channels.add(DeliveryChannel.valueOf(ch.trim()));
            } catch (Exception e) {
                // 알 수 없는 채널은 무시하기
            }
        }

        // IN_APP은 전송 채널이 아니므로 제거
        channels.remove(DeliveryChannel.IN_APP);
        return channels;
    }

}
