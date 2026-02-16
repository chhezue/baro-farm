package com.barofarm.ai.recommend.infrastructure.client;

import com.barofarm.ai.recommend.infrastructure.client.dto.CartInfo;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "shopping-service",
    path = "/api/v1/carts",
    configuration = CartFeignConfig.class
)
public interface CartClient {
    @GetMapping
    CartInfo getCart(@RequestHeader("X-User-Id") UUID userId);
}
