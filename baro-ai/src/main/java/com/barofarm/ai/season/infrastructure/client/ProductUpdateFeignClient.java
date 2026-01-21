package com.barofarm.ai.season.infrastructure.client;

import com.barofarm.ai.season.application.dto.SeasonalityUpdateRequest;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Buyer 서비스의 상품 제철 정보 업데이트를 위한 Feign Client
 */
@FeignClient(name = "buyer-service")
public interface ProductUpdateFeignClient {

    /**
     * 상품의 제철 정보를 업데이트
     *
     * @param productId       상품 ID
     * @param request         제철 정보 업데이트 요청 (type, value만 포함)
     */
    @PutMapping("/internal/products/{productId}/seasonality")
    void updateSeasonality(
            @PathVariable("productId") UUID productId,
            @RequestBody SeasonalityUpdateRequest request
    );
}
