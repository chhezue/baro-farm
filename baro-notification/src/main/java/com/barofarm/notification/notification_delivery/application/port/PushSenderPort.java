package com.barofarm.notification.notification_delivery.application.port;

public interface PushSenderPort {
    void send(String fcmToken, String title, String body, String deeplink);
}
