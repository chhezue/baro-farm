package com.barofarm.notification.notification_delivery.application.port;

public interface EmailSenderPort {
    void send(String toEmail, String subject, String body);
}
