package com.barofarm.support.notification_delivery.adapter.out.recipient;

import com.barofarm.support.notification_delivery.application.port.RecipientResolverPort;
import com.barofarm.support.notification_delivery.domain.model.RecipientProfile;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * auth-service에서 email/fcmToken을 조회하는 adapter
 *
 * Feign 사용 버전 (현재 support-service 묶여있는 상황에서)
 * - webflux 불필요
 * - MVC와 잘 맞음
 */
@Component
@RequiredArgsConstructor
public class RecipientResolverAdapter implements RecipientResolverPort {

    private final AuthInternalClient authInternalClient;

    @Override
    public RecipientProfile resolve(String userId) {
        Map<String, String> res = authInternalClient.getUserContact(userId);

        String email = res == null ? null : res.get("email");
        String token = res == null ? null : res.get("fcmToken");

        return new RecipientProfile(userId, email, token);
    }
}


///**
// * [auth-service에서 유저의 email, fcmToken을 가져오는 어댑터]
// *
// * TODO: 내부 호출이므로 Gateway bypass용 internal endpoint 만들어놓기 (AUTH-SERVICE)
// * 예 : GET http://auth-service/internal/users/{id}/contact
// * */
//@Component
//public class RecipientResolverAdapter implements RecipientResolverPort {
//
//    // reactive 라이브러리가 필요한데, 지금은 MVC로 돼 있을 텐데 어떻게...?
//    private fianl WebClient webClient;
//    private final String baseUrl;
//
//    public RecipientResolverAdapter(WebClient webClient, String baseUrl) {
//        this.webClient = webClient;
//        this.baseUrl = baseUrl;
//    }
//
//    @Override
//    public RecipientProfile resolve(String userId) {
//        // 예시 응답: { "userId":"1", "email":"a@b.com", "fcmToken":"xxxx" }
//        Map<String, String> res = webClient.get()
//            .uri(baseUrl + "/internal/users/" + userId + "/contact")
//            .retrieve()
//            .bodyToMono(Map.class)
//            .timeout(Duration.ofSeconds(2))
//            .block();
//
//        String email = res == null ? null : res.get("email");
//        String token = res == null ? null : res.get("fcmToken");
//
//        return new RecipientProfile(userId, email, token);
//    }
//
//}
