package com.barofarm.support.notification_delivery.adapter.out.recipient;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * auth-service 내부 API 호출 Feign Client
 *
 * 장점: MVC 프로젝트에 가장 자연스럽고, 추가 의존성 필요 없음 (일단 현재상황에선 이렇게 두기)
 * 운영에서 안정적
 *
 * TODO: 추후에 Reactive로 따로 서비스 분리되면 고려
 *
 * auth-service에 아래 엔드포인트가 필요:
 * GET /internal/users/{id}/contact
 * Response: { "email": "...", "fcmToken": "..." }
 */
@FeignClient(name = "baro-auth", url = "${notification.delivery.recipient.auth-base-url:http://baro-auth:8080}")
public interface AuthInternalClient {

    @GetMapping("/internal/users/{id}/contact")
    Map<String, String> getUserContact(@PathVariable("id") String userId);
}
