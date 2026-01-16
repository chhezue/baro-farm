package com.barofarm.support.notification_delivery.adapter.out.email;

import com.barofarm.support.notification_delivery.application.port.EmailSenderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP 기반 Email 발송 어댑터
 * */
@Component
@RequiredArgsConstructor
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;

    @Value("${notification.delivery.mail.from:no-reply@barofarm.com}")
    private String from;

    @Override
    public void send(String toEmail, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);

        mailSender.send(msg);
    }
}
