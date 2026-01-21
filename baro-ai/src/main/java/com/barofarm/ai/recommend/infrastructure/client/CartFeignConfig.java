package com.barofarm.ai.recommend.infrastructure.client;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CartFeignConfig {

    @Bean
    public ErrorDecoder cartFeignErrorDecoder() {
        return new CartFeignErrorDecoder();
    }
}
