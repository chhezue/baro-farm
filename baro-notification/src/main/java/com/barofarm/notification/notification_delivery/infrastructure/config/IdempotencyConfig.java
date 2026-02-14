package com.barofarm.notification.notification_delivery.infrastructure.config;

import com.barofarm.notification.notification_delivery.infrastructure.util.IdempotencyStore;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdempotencyConfig {

    @Bean
    public IdempotencyStore idempotencyStore() {
        return new IdempotencyStore(Duration.ofMinutes(10));
    }
}
