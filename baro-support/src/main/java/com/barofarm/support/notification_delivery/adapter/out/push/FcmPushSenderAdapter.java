package com.barofarm.support.notification_delivery.adapter.out.push;

import com.barofarm.support.notification_delivery.application.port.PushSenderPort;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

/**
 * FCM 푸시 발송 어댑터
 *
 * - token 대상 전송
 * - deeplink 같은 커스텀 데이터를 data payload로 넣을 수 있다.
 * */
@Component
public class FcmPushSenderAdapter implements PushSenderPort {

    @Override
    public void send(String fcmToken, String title, String body, String deeplink) {
        Message msg = Message.builder()
            .setToken(fcmToken)
            .setNotification(Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build())
            // 앱에서 클릭 시 이동을 원하면 data payload에 넣기
            .putData("deeplink", deeplink==null?"":deeplink)
            .build();

        try {
            FirebaseMessaging.getInstance().send(msg);
        } catch (Exception e) {
            throw new IllegalStateException("FCM send failed: " + e.getMessage(), e);
        }
    }
}
