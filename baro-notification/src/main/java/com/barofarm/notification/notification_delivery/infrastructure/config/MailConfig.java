package com.barofarm.notification.notification_delivery.infrastructure.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 메일 발송용 JavaMailSender 설정.
 * 타임아웃/SSL 등 SMTP 옵션을 명시적으로 제어한다.
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(org.springframework.core.env.Environment env) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        sender.setHost(env.getProperty("spring.mail.host"));
        sender.setPort(Integer.parseInt(env.getProperty("spring.mail.port", "587")));
        sender.setUsername(env.getProperty("spring.mail.username"));
        sender.setPassword(env.getProperty("spring.mail.password"));

        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        String starttlsEnable = env.getProperty(
            "spring.mail.properties.mail.smtp.starttls.enable",
            "false"
        );
        String sslEnable = env.getProperty(
            "spring.mail.properties.mail.smtp.ssl.enable",
            "false"
        );
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", env.getProperty("spring.mail.properties.mail.smtp.auth", "true"));
        props.put("mail.smtp.starttls.enable", starttlsEnable);
        props.put("mail.smtp.ssl.enable", sslEnable);
        props.put("mail.smtp.ssl.trust", env.getProperty("spring.mail.properties.mail.smtp.ssl.trust"));

        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }
}

