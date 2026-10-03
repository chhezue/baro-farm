package com.barofarm.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * LLM 챗 호출용 {@link ChatClient} 설정.
 * 모델은 Google Gemini를 쓴다 — 근거: docs/decisions/003-gemini-migration.md
 * 기본 옵션(모델·temperature)은 application-prod.yml에 있고, 호출마다 달라야 하는 값은 호출부에서 덮어쓴다.
 * 타임아웃·재시도는 Spring AI 설정(spring.ai.retry.*)이 아니라 여기서 정한다. Spring AI의 재시도는
 * 자기 예외 타입만 대상으로 삼아 Gemini SDK가 던지는 429·5xx 예외에는 걸리지 않고, 실제 재시도는
 * SDK 안의 HTTP 인터셉터가 한다. 근거: docs/audits/cross-cutting/001-llm-call-infra.md A-1
 */
@Configuration
public class LlmClientConfig {

    // Gemini 호출 1회의 상한. spring.ai.google.genai.* 설정에는 타임아웃 속성이 없어서
    // Client 빈을 직접 만들어 넣어야 한다. 상한이 없으면 Gemini가 응답하지 않을 때
    // 추천 요청 스레드와 Kafka 리스너 스레드가 무기한 붙잡힌다.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    // 재시도를 포함한 총 시도 횟수. SDK 기본값은 5회에 지연 상한 60초라, 아무 값도 안 주면
    // 한 번의 요청이 최악 5 × 30초 + 지연으로 몇 분까지 늘어질 수 있었다.
    // 3회면 429(무료 한도)·5xx 같은 순간 장애는 넘기면서 최악 시간이 약 3 × 30초 + 3초에 머문다.
    // 재시도 대상 상태 코드는 SDK 기본(408·429·500·502·503·504)을 그대로 쓴다.
    private static final int RETRY_TOTAL_ATTEMPTS = 3;
    private static final double RETRY_INITIAL_DELAY_SECONDS = 1.0;
    private static final double RETRY_MAX_DELAY_SECONDS = 10.0;

    @Bean
    Client googleGenAiClient(Environment environment) {
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
            .attempts(RETRY_TOTAL_ATTEMPTS)
            .initialDelay(RETRY_INITIAL_DELAY_SECONDS)
            .maxDelay(RETRY_MAX_DELAY_SECONDS)
            .build();
        return Client.builder()
            .apiKey(environment.getRequiredProperty("spring.ai.google.genai.api-key"))
            .httpOptions(HttpOptions.builder()
                .timeout((int) CALL_TIMEOUT.toMillis())
                .retryOptions(retryOptions)
                .build())
            .build();
    }

    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
