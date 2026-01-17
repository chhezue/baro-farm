package com.barofarm.auth.presentation;

import com.barofarm.auth.application.AuthService;
import com.barofarm.auth.application.usecase.OAuthLinkStartResult;
import com.barofarm.auth.application.usecase.OAuthLoginStateResult;
import com.barofarm.auth.application.usecase.TokenResult;
import com.barofarm.auth.infrastructure.security.AuthUserPrincipal;
import com.barofarm.auth.presentation.api.OAuthSwaggerApi;
import com.barofarm.auth.presentation.dto.oauth.OAuthCallbackRequest;
import com.barofarm.auth.presentation.dto.oauth.OAuthLinkCallbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class OAuthController implements OAuthSwaggerApi {

    private final AuthService authService;

    @PostMapping("/oauth/callback")
    public ResponseEntity<TokenResult> oauthCallback(@RequestBody OAuthCallbackRequest request) {
        // 소셜 로그인 콜백: state 검증 + 사용자 매핑 + JWT 발급까지 처리한다.
        TokenResult response = authService.oauthCallback(request.toServiceRequest());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth/state")
    public ResponseEntity<OAuthLoginStateResult> issueLoginState() {
        // 로그인 시작 단계에서 필요한 state를 발급한다.
        OAuthLoginStateResult response = authService.startOAuthLogin();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/oauth/link/start")
    public ResponseEntity<OAuthLinkStartResult> startLink(@AuthenticationPrincipal AuthUserPrincipal principal) {
        // 로그인된 사용자에게만 연결용 state를 발급한다.
        OAuthLinkStartResult response = authService.startOAuthLink(principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth/link/callback")
    public ResponseEntity<Void> linkCallback(
        @AuthenticationPrincipal AuthUserPrincipal principal,
        @RequestBody OAuthLinkCallbackRequest request
    ) {
        // 연결 콜백: link_state와 현재 사용자 일치 여부를 확인한다.
        authService.oauthLinkCallback(request.toServiceRequest(principal.getUserId()));
        return ResponseEntity.ok().build();
    }
}
