package com.barofarm.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

/**
 * 요청 경로 전용 채팅 클라이언트가 실제로 조립되는지 확인한다.
 *
 * <p>이 확인이 필요한 이유는 이 빈만 자동설정을 거치지 않기 때문이다. 배치 경로 클라이언트는 Spring AI가
 * 만들어 주지만, 요청 경로는 짧은 타임아웃을 가진 클라이언트로 모델을 직접 조립한다. 조립에 필요한 것을
 * 하나라도 빠뜨리면 애플리케이션이 뜨는 순간에야 드러나고, 이 저장소에는 기동을 확인하는 테스트가 없어
 * 그때까지 아무도 모른다. 그래서 조립만 따로 떼어 여기서 확인한다. 실제 호출은 하지 않는다.
 */
class LlmClientConfigTest {

    @Test
    @DisplayName("요청 경로 ChatClient가 예외 없이 조립된다 — 기동 시점에야 드러나는 배선 오류를 여기서 잡는다")
    void interactiveChatClient_isAssembled() {
        LlmClientConfig config = new LlmClientConfig();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.ai.google.genai.api-key", "test-key")
            .withProperty("spring.ai.google.genai.chat.options.model", "gemini-3.5-flash-lite")
            .withProperty("spring.ai.google.genai.chat.options.temperature", "0.7");

        Client interactiveClient = config.interactiveGenAiClient(environment);

        ChatClient chatClient = config.interactiveChatClient(
            interactiveClient,
            mock(ToolCallingManager.class),
            noopObservationRegistry(),
            environment);

        assertThat(chatClient).isNotNull();
    }

    @Test
    @DisplayName("요청 경로에 온도 설정이 빠지면 기동을 실패시킨다 — 골든셋 측정 조건이 조용히 달라지는 것을 막는다")
    void interactiveChatClient_requiresTemperature() {
        LlmClientConfig config = new LlmClientConfig();
        MockEnvironment noTemperature = new MockEnvironment()
            .withProperty("spring.ai.google.genai.api-key", "test-key")
            .withProperty("spring.ai.google.genai.chat.options.model", "gemini-3.5-flash-lite");

        Client client = config.interactiveGenAiClient(noTemperature);

        assertThatThrownBy(() -> config.interactiveChatClient(
            client, mock(ToolCallingManager.class), noopObservationRegistry(), noTemperature))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("배치 경로와 요청 경로가 서로 다른 클라이언트를 쓴다 — 예산이 갈리는 지점이다")
    void batchAndInteractiveClientsAreSeparate() {
        LlmClientConfig config = new LlmClientConfig();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.ai.google.genai.api-key", "test-key");

        assertThat(config.googleGenAiClient(environment))
            .isNotSameAs(config.interactiveGenAiClient(environment));
    }

    /**
     * 요청 경로에서 SDK 기본 재시도가 정말 꺼져 있는지 실제 요청 수로 확인한다.
     *
     * <p>2026-09-09까지 이 클라이언트는 재시도 옵션을 비워 뒀는데, google-genai SDK는 옵션이 비면 기본 5회
     * 재시도를 설치한다(docs/audits/cart-recipe/010 A-1). 429를 즉시 돌려주는 로컬 스텁에 3초 상한으로
     * 한 번 부르면 우리 옵션은 정확히 한 번만 요청한다. SDK 버전을 올렸을 때 이 계약이 바뀌면 여기서 잡힌다.
     * Gemini 키·네트워크는 필요 없다.
     *
     * <p>"옵션을 비우면 SDK가 알아서 재시도한다"는 대조군은 이 테스트에 두지 않았다 — SDK의 재시도 대기는
     * 타임아웃으로 끊기지 않아 한 번 도는 데 십수 초가 걸린다(리뷰 실측 약 14초). 그 사실은 바이트코드와
     * 1회 실행으로 확인해 감사 010 A-1에 적어 뒀다.
     */
    @Test
    @DisplayName("요청 경로 옵션은 429에도 딱 한 번만 요청한다 — SDK 기본 재시도가 붙지 않았다")
    void interactiveOptions_doNotRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"error\":{\"code\":429,\"message\":\"quota\",\"status\":\"RESOURCE_EXHAUSTED\"}}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        stub.start();
        String baseUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
        try {
            // 우리 옵션: 재시도 1회·대기 0
            Client ours = Client.builder()
                .apiKey("test-key")
                .httpOptions(LlmClientConfig.httpOptions(Duration.ofSeconds(3), 1).baseUrl(baseUrl).build())
                .build();
            assertThatThrownBy(() -> ours.models.generateContent("gemini-3.5-flash-lite", "hi", null))
                .isInstanceOf(RuntimeException.class);
            assertThat(requests.get()).as("우리 옵션은 정확히 한 번 요청한다").isEqualTo(1);
        } finally {
            stub.stop(0);
        }
    }

    /** 지표 수집기가 없는 환경을 흉내 낸다 — 설정이 그 경우를 견디는지도 함께 본다. */
    private static ObjectProvider<ObservationRegistry> noopObservationRegistry() {
        return new ObjectProvider<>() {
            @Override
            public ObservationRegistry getObject() throws BeansException {
                return ObservationRegistry.NOOP;
            }
        };
    }
}
