package com.barofarm.gateway.filter;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


/*
 *   OpaAuthorizationGatewayFilterFactory는 인가(Authorization) 담당
 *     - 하는 일: 위에서 만든 헤더 + 요청 정보(method/path) + 라우트 정보(service)를 OPA로 보내서 허용/차단 결정 받음
 *     - 결과: OPA가 true면 통과, false면 403, 오류면 503
 *     - 즉 “이 사용자가 이 요청을 해도 되는지 판단” 단계
 */
@Component
public class OpaAuthorizationGatewayFilterFactory
    extends AbstractGatewayFilterFactory<OpaAuthorizationGatewayFilterFactory.Config> {

    private static final String HEADER_USER_STATUS = "X-User-Status";
    private static final String HEADER_SELLER_STATUS = "X-Seller-Status";
    private static final String HEADER_USER_FLAGS = "X-User-Flags";

    private final WebClient webClient;
    private final String authzUrl;
    private final Duration timeout;
    private final CircuitBreaker opaCircuitBreaker;
    private final Bulkhead opaBulkhead;

    public OpaAuthorizationGatewayFilterFactory(
        WebClient.Builder webClientBuilder,
        CircuitBreakerRegistry circuitBreakerRegistry,
        BulkheadRegistry bulkheadRegistry,
        @Value("${opa.url:http://opa:8181}") String opaUrl,
        @Value("${opa.authz-path:/v1/data/gateway/authz/allow}") String authzPath,
        @Value("${opa.timeout-ms:2000}") long timeoutMs
    ) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
        this.authzUrl = opaUrl + authzPath;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.opaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("opaAuthz");
        this.opaBulkhead = bulkheadRegistry.bulkhead("opaAuthz");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            Map<String, Object> input = buildInput(exchange);
            // OPA 입력을 확인하고 싶을 때 사용하는 디버그 포인트.
            // 필요한 경우 로깅 코드(예: log.debug("OPA input: {}", input))를 활성화하세요.
            Mono<OpaResponse> responseMono = webClient.post()
                .uri(authzUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("input", input))
                .retrieve()
                .bodyToMono(OpaResponse.class);

            return responseMono
                .transformDeferred(CircuitBreakerOperator.of(opaCircuitBreaker))
                .transformDeferred(BulkheadOperator.of(opaBulkhead))
                .timeout(timeout)
                .flatMap((OpaResponse response) -> {
                    boolean allowed = response != null && Boolean.TRUE.equals(response.getResult());
                    return allowed
                        ? chain.filter(exchange)
                        : deny(exchange, HttpStatus.FORBIDDEN);
                })
                .onErrorResume(ex -> deny(exchange, HttpStatus.SERVICE_UNAVAILABLE));
        };
    }

    private Map<String, Object> buildInput(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        String role = request.getHeaders().getFirst("X-User-Role");
        List<String> roles = role == null || role.isBlank() ? List.of("ANONYMOUS") : List.of(role);

        // Fixed input schema for OPA decisions.
        String userStatus = request.getHeaders().getFirst(HEADER_USER_STATUS);
        String sellerStatus = request.getHeaders().getFirst(HEADER_SELLER_STATUS);
        List<String> flags = parseFlags(request.getHeaders().getFirst(HEADER_USER_FLAGS));
        String effectiveUserStatus = userStatus == null || userStatus.isBlank() ? "ACTIVE" : userStatus;
        String effectiveSellerStatus = sellerStatus == null || sellerStatus.isBlank() ? "UNKNOWN" : sellerStatus;

        Map<String, Object> subject = new HashMap<>();
        subject.put("id", request.getHeaders().getFirst("X-User-Id"));
        subject.put("email", request.getHeaders().getFirst("X-User-Email"));
        subject.put("roles", roles);
        subject.put("flags", flags);
        // Status/flags allow OPA to block requests without hitting downstream services.
        subject.put("user_status", effectiveUserStatus);
        subject.put("seller_status", effectiveSellerStatus);

        String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod().name();
        Map<String, Object> input = new HashMap<>();
        input.put("schema_version", 1);
        input.put("request", Map.of(
            "method", method,
            "path", request.getPath().value()));
        input.put("subject", subject);
        String service = resolveService(exchange);
        if (service != null && !service.isBlank()) {
            input.put("route", Map.of("service", service));
        }
        return input;
    }

    private List<String> parseFlags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String text = part.trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    private String resolveService(ServerWebExchange exchange) {
        Object routeAttr = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (!(routeAttr instanceof Route route)) {
            return null;
        }
        String routeId = route.getId();
        if (routeId.endsWith("-service-openapi")) {
            return routeId.substring(0, routeId.length() - "-service-openapi".length());
        }
        if (routeId.endsWith("-service")) {
            return routeId.substring(0, routeId.length() - "-service".length());
        }
        return routeId;
    }

    public static class Config {
    }

    public static class OpaResponse {
        private Boolean result;

        public Boolean getResult() {
            return result;
        }

        public void setResult(Boolean result) {
            this.result = result;
        }
    }
}
