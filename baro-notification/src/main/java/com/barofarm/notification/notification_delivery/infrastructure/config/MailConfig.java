package com.barofarm.notification.notification_delivery.infrastructure.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 메일 발송용 JavaMailSender 설정.
 * 타임아웃/SSL 등 SMTP 옵션을 명시적으로 제어한다.
 *
 * SMTP 설정
 *
 * Spring Boot starter-mail이 있어도 application.yml로 설정하면 자동 구성됨
 * 다만 아래처럼 Bean을 명시하면:
 * - 운영에서 튜닝(타임아웃/인코딩) 통제가 쉬워진다.
 *
 * 주의:
 * - 이미 spring.mail.* 설정을 쓰고 있다면
 *   이 Bean을 굳이 만들지 않아도 된다.
 * - "커스텀 튜닝이 필요하면" 사용하는 용도다.
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
