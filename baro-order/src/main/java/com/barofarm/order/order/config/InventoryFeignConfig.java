package com.barofarm.order.order.config;

import com.barofarm.order.order.infrastructure.rest.InventoryErrorDecoder;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class InventoryFeignConfig {

    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(
            2, TimeUnit.SECONDS,
            3, TimeUnit.SECONDS,
            true
        );
    }

    @Bean
    public Retryer feignRetryer() {
        long delayMs = 500L;
        int maxAttempts = 3;
        return new Retryer.Default(delayMs, delayMs, maxAttempts);
    }

    @Bean
    public ErrorDecoder inventoryErrorDecoder() {
        return new InventoryErrorDecoder();
    }
}
