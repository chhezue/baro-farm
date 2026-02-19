package com.barofarm.order.review.client.product;

import com.barofarm.order.review.client.product.dto.ProductResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "shopping-service",
    configuration = ProductFeignConfig.class
)
public interface ProductClient {

    @GetMapping("/internal/products/{id}")
    ProductResponse getProduct(@PathVariable("id") UUID productId);
}
