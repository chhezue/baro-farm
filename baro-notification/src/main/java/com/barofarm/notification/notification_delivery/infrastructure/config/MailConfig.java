package com.barofarm.notification.notification_delivery.infrastructure.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SMTP ?ㅼ젙
 *
 * Spring Boot starter-mail???덉뼱??application.yml濡??ㅼ젙?섎㈃ ?먮룞 援ъ꽦??
 * ?ㅻ쭔 ?꾨옒泥섎읆 Bean??紐낆떆?섎㈃:
 * - ?댁쁺?먯꽌 ?쒕떇(??꾩븘???몄퐫?? ?듭젣媛 ?ъ썙吏꾨떎.
 *
 * 二쇱쓽:
 * - ?대? spring.mail.* ?ㅼ젙???곌퀬 ?덈떎硫?
 *   ??Bean??援녹씠 留뚮뱾吏 ?딆븘???쒕떎.
 * - "而ㅼ뒪? ?쒕떇???꾩슂?섎㈃" ?ъ슜?섎뒗 ?⑸룄??
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

        // ?댁쁺?먯꽌 SMTP hang 諛⑹? (ms)
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }
}
