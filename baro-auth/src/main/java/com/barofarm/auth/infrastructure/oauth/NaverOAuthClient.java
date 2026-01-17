package com.barofarm.auth.infrastructure.oauth;

import com.barofarm.auth.application.port.out.OAuthProviderClient;
import com.barofarm.auth.domain.oauth.OAuthProvider;
import com.barofarm.auth.domain.oauth.OAuthUserInfo;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverOAuthClient implements OAuthProviderClient {

    private final NaverOAuthProperties properties;
    private final RestClient restClient;

    public NaverOAuthClient(NaverOAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public OAuthUserInfo fetchUserInfo(OAuthProvider provider, String code, String state) {
        if (provider != OAuthProvider.NAVER) {
            // 현재는 네이버만 지원하므로 명시적으로 차단한다.
            throw new IllegalArgumentException("Unsupported OAuth provider: " + provider);
        }

        TokenResponse token = requestAccessToken(code, state);
        NaverUserInfoResponse userInfo = requestUserInfo(token.access_token());

        return new OAuthUserInfo(
            OAuthProvider.NAVER,
            userInfo.response().id(),
            userInfo.response().email(),
            userInfo.response().name(),
            userInfo.response().mobile()
        );
    }

    private TokenResponse requestAccessToken(String code, String state) {
        // 네이버 토큰 교환은 쿼리 파라미터 기반으로 진행한다.
        URI uri = UriComponentsBuilder.fromUriString(properties.getTokenUri())
            .queryParam("grant_type", "authorization_code")
            .queryParam("client_id", properties.getClientId())
            .queryParam("client_secret", properties.getClientSecret())
            .queryParam("code", code)
            .queryParam("state", state)
            .queryParam("redirect_uri", properties.getRedirectUri())
            .build(true)
            .toUri();

        return restClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .retrieve()
            .body(TokenResponse.class);
    }

    private NaverUserInfoResponse requestUserInfo(String accessToken) {
        // Authorization 헤더로 사용자 정보를 요청한다.
        return restClient.get()
            .uri(properties.getUserInfoUri())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(NaverUserInfoResponse.class);
    }

    private record TokenResponse(
        String access_token,
        String token_type,
        String refresh_token,
        int expires_in
    ) {
    }

    private record NaverUserInfoResponse(
        String resultcode,
        String message,
        NaverUser response
    ) {
    }

    private record NaverUser(
        String id,
        String email,
        String name,
        String mobile
    ) {
    }
}
