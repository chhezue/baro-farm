package com.barofarm.notification.notification_delivery.adapter.out.push;

import com.barofarm.notification.notification_delivery.application.port.PushSenderPort;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

/**
 *
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
            .putData("deeplink", deeplink==null?"":deeplink)
            .build();

        try {
            FirebaseMessaging.getInstance().send(msg);
        } catch (Exception e) {
            throw new IllegalStateException("FCM send failed: " + e.getMessage(), e);
        }
    }
}
