package com.barofarm.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;

/**
 * Gemini 호출 클라이언트를 **경로 두 개로 나눠** 등록한다.
 *
 * 나눈 이유: 같은 30초 × 3회 정책을 두 경로가 공유하고 있었는데, 두 경로가 견딜 수 있는 지연이 30배 다르다.
 * 색인·요약은 사람이 기다리지 않으므로 오래 걸려도 되지만, 레시피 추천은 사용자가 화면 앞에서 기다린다.
 * 한쪽에 맞춘 값이 다른 쪽에서 장애가 됐다 — 요청 스레드 하나가 30초 넘게 묶일 수 있었고, 그 스레드는
 * Tomcat 공용 풀에서 나오므로 Gemini가 느려지면 검색·자동완성처럼 LLM과 무관한 API까지 함께 죽는 구조였다.
 *
 * <p>SDK가 호출별 타임아웃을 받지 않아({@link HttpOptions}는 클라이언트 단위다) 클라이언트를 둘로 나눴다.
 * 요청 경로 클라이언트는 3초에 끊고 재시도하지 않는다 — 재시도는 사용자를 더 기다리게 할 뿐이고,
 * 레시피를 못 만든 것은 오류가 아니라 빈 응답으로 나가는 것이 이 도메인의 정책이기 때문이다.
 *
 * <p><b>재시도 옵션은 두 경로 모두 반드시 명시한다.</b> google-genai SDK는 {@code retryOptions}가 비어 있으면
 * 기본값(5회 시도, 지수 백오프, 408·429·5xx와 타임아웃 취소까지 재시도)으로 인터셉터를 항상 설치한다.
 * 2026-09-09까지 요청 경로는 "재시도 없음"이라며 옵션을 비워 뒀는데, 그 결과 3초 타임아웃 뒤에도 SDK가
 * 평균 18초·최악 약 33초를 더 잠들며 재시도하고 있었다(docs/audits/cart-recipe/010 A-1). 타임아웃은
 * 호출 전체({@code callTimeout})를 감싸므로 배치 경로의 최악도 "30초 × 3회"가 아니라 약 36초다.
 *
 * <p>동시 호출 수 제한(격벽)은 여기가 아니라 {@code LlmGateway}가 맡는다.
 */
@Configuration
public class LlmClientConfig {

    /** 배치 경로(색인 재료명 추출·리뷰 요약) 호출 1회의 상한 */
    private static final Duration BATCH_CALL_TIMEOUT = Duration.ofSeconds(30);

    /** 배치 경로에서 일시적 오류(429·5xx)에 대응하는 총 시도 횟수 */
    private static final int BATCH_RETRY_ATTEMPTS = 3;

    /** 첫 번째 재시도 전 초기 대기 시간 (1.0초) */
    private static final double RETRY_INITIAL_DELAY_SECONDS = 1.0;

    /** 지수 백오프로 증가하는 재시도 대기 시간의 상한 (10.0초) */
    private static final double RETRY_MAX_DELAY_SECONDS = 10.0;

    /**
     * 요청 경로(레시피 추천) 호출 1회의 상한.
     *
     * 게이트웨이 응답 상한(5초)보다 짧게 둔다 — 그보다 오래 붙잡아 봐야 사용자는 이미 끊긴 뒤다.
     * 장바구니 조회(최대 3초)와 부족 재료 검색이 같은 요청 안에서 앞뒤로 붙으므로 3초로 맞췄다.
     */
    private static final Duration INTERACTIVE_CALL_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 요청 경로에서 저수준 통신 클라이언트({@link Client})를 만든다.
     *
     * @param timeout  호출 1회의 상한
     * @param attempts 총 시도 횟수 (1이면 재시도 없음)
     */
    private static Client buildClient(String apiKey, Duration timeout, int attempts) {
        return Client.builder()
            .apiKey(apiKey)
            .httpOptions(httpOptions(timeout, attempts).build())
            .build();
    }

    /**
     * 통신 옵션만 따로 조립한다 — 테스트가 {@code baseUrl}을 로컬 스텁으로 바꿔 끼워 실제 요청 수를 셀 수 있게.
     *
     * <p>{@code attempts}가 1이어도 재시도 옵션을 비워 두지 않는다. 비우면 SDK가 기본 5회 재시도를 설치한다.
     * 1회일 때는 대기까지 0으로 둔다 — {@code attempts(1)}만으로는 429 응답 뒤 한 번 잠드는 경로가 남는다.
     */
    static HttpOptions.Builder httpOptions(Duration timeout, int attempts) {
        HttpRetryOptions.Builder retry = HttpRetryOptions.builder().attempts(attempts);
        if (attempts > 1) {
            retry.initialDelay(RETRY_INITIAL_DELAY_SECONDS).maxDelay(RETRY_MAX_DELAY_SECONDS);
        } else {
            retry.initialDelay(0.0).maxDelay(0.0);
        }
        return HttpOptions.builder()
            .timeout((int) timeout.toMillis())
            .retryOptions(retry.build());
    }

    private static String apiKey(Environment environment) {
        return environment.getRequiredProperty("spring.ai.google.genai.api-key");
    }

    /**
     * 배치 경로용 Google GenAI 클라이언트.
     *
     * {@code @Primary}인 이유: Spring AI 자동설정이 채팅 모델과 임베딩 모델을 만들 때 이 타입의 빈을 찾는다.
     * 임베딩(색인 경로)과 리뷰 요약은 둘 다 배치 성격이라 이쪽 정책이 맞다.
     */
    @Bean
    @Primary
    Client googleGenAiClient(Environment environment) {
        return buildClient(apiKey(environment), BATCH_CALL_TIMEOUT, BATCH_RETRY_ATTEMPTS);
    }

    /** 요청 경로용 Google GenAI 클라이언트 — 3초에 끊고 재시도하지 않는다(SDK 기본 재시도까지 끈 상태). */
    @Bean
    Client interactiveGenAiClient(Environment environment) {
        return buildClient(apiKey(environment), INTERACTIVE_CALL_TIMEOUT, 1);
    }

    /**
     * 배치 경로 {@link ChatClient}. 자동설정이 만든 채팅 모델을 그대로 감싼다.
     *
     * {@code @Primary}인 이유: 이 저장소에서 {@code ChatClient}를 한정자 없이 주입받는 곳
     * (리뷰 요약)이 배치 성격이기 때문이다.
     */
    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 요청 경로 {@link ChatClient}.
     *
     * 자동설정은 클라이언트 하나로 채팅 모델 하나만 만들므로, 짧은 상한을 가진 클라이언트로
     * 모델을 직접 조립한다. 재시도는 SDK 쪽에서 이미 끈 상태이고, Spring AI 쪽 {@link RetryTemplate}도
     * 1회로 맞춰 두 겹이 겹치지 않게 한다.
     */
    @Bean
    public ChatClient interactiveChatClient(
        @Qualifier("interactiveGenAiClient") Client interactiveGenAiClient,
        ToolCallingManager toolCallingManager,
        ObjectProvider<ObservationRegistry> observationRegistry,
        Environment environment
    ) {
        // 온도를 여기서 반드시 넣는다. 자동설정이 만드는 배치 경로 모델은 `spring.ai.google.genai.chat.options.*`를
        // 알아서 읽지만, 직접 조립하는 이 모델은 넣지 않으면 비어 있는 채로 나가 Gemini 자체 기본값이 적용된다.
        // 레시피 프롬프트는 온도 0.7에서 골든셋으로 검증한 문장이라(실행 합격률 69.0% → 90.5%),
        // 값이 조용히 달라지면 그 측정이 무효가 된다. 그래서 없으면 기동을 실패시킨다 —
        // 여기서 기본값을 대신 적어 두면 yml과 코드 두 곳에 같은 값이 생겨 또 갈라진다.
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
            .model(environment.getRequiredProperty("spring.ai.google.genai.chat.options.model"))
            .temperature(environment.getRequiredProperty(
                "spring.ai.google.genai.chat.options.temperature", Double.class))
            .build();

        GoogleGenAiChatModel chatModel = new GoogleGenAiChatModel(
            interactiveGenAiClient,
            options,
            toolCallingManager,
            RetryTemplate.builder().maxAttempts(1).build(),
            observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));

        return ChatClient.builder(chatModel).build();
    }
}
