package com.barofarm.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
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
 */
@Configuration
public class LlmClientConfig {

    // Gemini 호출 상한. Spring AI의 spring.ai.google.genai.* 설정에는 타임아웃 속성이 없어서
    // Client 빈을 직접 만들어 넣어야 한다. 상한이 없으면 Gemini가 응답하지 않을 때
    // 추천 요청 스레드와 Kafka 리스너 스레드가 무기한 붙잡힌다.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    Client googleGenAiClient(Environment environment) {
        return Client.builder()
            .apiKey(environment.getRequiredProperty("spring.ai.google.genai.api-key"))
            .httpOptions(HttpOptions.builder().timeout((int) CALL_TIMEOUT.toMillis()).build())
            .build();
    }

    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
