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
 * Google Gemini 대화형 LLM 호출을 위한 {@link Client} 및 {@link ChatClient} 빈 설정 클래스입니다.
 *
 * [핵심 아키텍처 및 장애 방어]
 * 1. 호출 타임아웃 제한 (30초): Google GenAI 호출 1회의 상한을 30초로 제한하여, LLM API 지연 시 추천 스레드 및 Kafka 리스너가 무기한 블로킹되는 장애를 원천 차단합니다.
 * 2. 지수 백오프 재시도 (최대 3회): 순간적인 429(Rate Limit) 또는 5xx 서버 오류에 대응할 수 있도록 SDK 내부 HTTP 인터셉터 수준에서 최대 3회 재시도를 수행합니다.
 * 3. ChatClient 빈 등록: Spring AI의 고수준 추상화 대화형 클라이언트를 생성하여 레시피 후보 생성기 등에서 주입받아 사용할 수 있도록 제공합니다.
 */
@Configuration
public class LlmClientConfig {

    /** Gemini API 호출 1회의 최대 대기 시간 상한선 (30초) */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    /** 일시적 오류(429, 5xx) 발생 시 재시도를 포함한 총 최대 시도 횟수 (3회) */
    private static final int RETRY_TOTAL_ATTEMPTS = 3;

    /** 첫 번째 재시도 전 초기 대기 시간 (1.0초) */
    private static final double RETRY_INITIAL_DELAY_SECONDS = 1.0;

    /** 지수 백오프로 증가하는 재시도 대기 시간의 최대 상한선 (10.0초) */
    private static final double RETRY_MAX_DELAY_SECONDS = 10.0;

    /**
     * Google GenAI 공식 Java SDK의 저수준 통신 클라이언트({@link Client}) 빈을 등록합니다.
     *
     * @param environment 애플리케이션 프로퍼티 환경 객체
     * @return 타임아웃과 재시도 정책이 바인딩된 Google GenAI Client 객체
     */
    @Bean
    Client googleGenAiClient(Environment environment) {
        // [1단계: HTTP 지수 백오프 재시도 옵션 구성]
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
            .attempts(RETRY_TOTAL_ATTEMPTS)
            .initialDelay(RETRY_INITIAL_DELAY_SECONDS)
            .maxDelay(RETRY_MAX_DELAY_SECONDS)
            .build();

        // [2단계: 타임아웃 및 API 키를 적용한 Google GenAI Client 인스턴스 빌드]
        return Client.builder()
            .apiKey(environment.getRequiredProperty("spring.ai.google.genai.api-key"))
            .httpOptions(HttpOptions.builder()
                .timeout((int) CALL_TIMEOUT.toMillis())
                .retryOptions(retryOptions)
                .build())
            .build();
    }

    /**
     * Spring AI의 대화형 추상화 클라이언트({@link ChatClient}) 빈을 등록합니다.
     *
     * @param chatModel Google GenAI 대화 모델 구현체 (googleGenAiChatModel)
     * @return 애플리케이션 전역에서 주입받아 사용할 ChatClient 인스턴스
     */
    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
