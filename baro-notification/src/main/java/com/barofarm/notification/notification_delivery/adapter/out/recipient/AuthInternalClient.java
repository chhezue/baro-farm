package com.barofarm.notification.notification_delivery.adapter.out.recipient;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 *
 *
 * GET /internal/users/{id}/contact
 * Response: { "email": "...", "fcmToken": "..." }
 */
@FeignClient(name = "baro-auth", url = "${notification.delivery.recipient.auth-base-url:http://baro-auth:8080}")
public interface AuthInternalClient {

    @GetMapping("/internal/users/{id}/contact")
    Map<String, String> getUserContact(@PathVariable("id") String userId);
}
