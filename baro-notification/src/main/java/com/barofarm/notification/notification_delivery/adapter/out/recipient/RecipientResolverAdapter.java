package com.barofarm.notification.notification_delivery.adapter.out.recipient;

import com.barofarm.notification.notification_delivery.application.port.RecipientResolverPort;
import com.barofarm.notification.notification_delivery.domain.model.RecipientProfile;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 *
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
// *
// * ??: GET http://auth-service/internal/users/{id}/contact
// * */
//@Component
//public class RecipientResolverAdapter implements RecipientResolverPort {
//
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
