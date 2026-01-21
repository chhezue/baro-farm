package com.barofarm.payment.payment.infrastructure.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class DepositClient {

    private final RestTemplate restTemplate;

    @Value("${support-service.base-url:http://localhost:8089}")
    private String supportServiceBaseUrl;

    public void refundDeposit(UUID userId, UUID orderId, long amount) {
        String url = supportServiceBaseUrl + "/internal/deposits/refund";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("amount", amount);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForObject(url, entity, Void.class);
    }
}
