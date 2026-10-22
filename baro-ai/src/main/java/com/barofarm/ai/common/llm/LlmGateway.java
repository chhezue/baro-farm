package com.barofarm.ai.common.llm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Gemini 호출이 지나가는 단일 관문.
 *
 * 이 클래스를 만든 이유는 호출 지점마다 정책이 달랐기 때문이다. 색인 경로와 추천 경로가 같은
 * 30초 × 3회를 쓰고 있었고, 응답 형식을 스키마로 못 박는 곳과 프롬프트 문장으로만 부탁하는 곳이
 * 섞여 있었으며, 실패했을 때 남는 흔적은 로그 한 줄뿐이라 "LLM이 요즘 얼마나 실패하는지"를
 * 아무도 알 수 없었다. 관문을 하나 두면 이 셋을 한 곳에서 정한다.
 *
 * <p><b>여기서 정하는 것</b>
 * <ol>
 *   <li><b>예산</b> — {@link LlmCallKind}에 따라 3초짜리 클라이언트와 30초짜리 클라이언트 중 하나를 고른다</li>
 *   <li><b>격벽</b> — 요청 경로의 동시 호출을 {@value #INTERACTIVE_MAX_CONCURRENT}개로 묶는다.
 *       Gemini가 느려져도 Tomcat 요청 스레드가 그 수만큼만 잡히므로, 검색·자동완성 같은
 *       LLM과 무관한 API가 함께 죽지 않는다. 자리가 없으면 기다리지 않고 즉시 빈 결과다 —
 *       기다리게 하면 격벽을 둔 의미가 없다</li>
 *   <li><b>구조화 출력</b> — 응답 타입의 JSON 스키마를 Gemini에 그대로 넘긴다. 프롬프트로 형식을
 *       부탁하는 것보다 훨씬 안정적이다(그래도 파싱 실패가 0이 되지는 않아 아래에서 예외를 잡는다)</li>
 *   <li><b>관측</b> — 호출 수·지연·결과를 {@code llm.call} 타이머에 남긴다. 태그는 종류와 결과이고,
 *       결과는 {@code success}·{@code empty}·{@code failed}(타임아웃·429·파싱 실패)·{@code rejected}(격벽) 넷이다</li>
 *   <li><b>실패의 표현</b> — 예외를 올리지 않고 {@link Optional#empty()}로 돌려준다. LLM 실패는
 *       이 서비스에서 오류가 아니라 "이번엔 못 만들었다"는 상태이고, 호출자들이 전부 그렇게 다룬다</li>
 * </ol>
 *
 * <p><b>여기서 정하지 않는 것</b>: 프롬프트 문구. 문구는 호출자가 소유한다 — 레시피 프롬프트는
 * 골든셋 15종 × 3회로 검증된 문장이라(69.0% → 90.5%) 이 관문이 임의로 손대면 그 측정이 무효가 된다.
 * 외부에서 들어온 값을 프롬프트에 끼워 넣을 때는 {@link #asData(String)}를 거친다.
 */
@Slf4j
@Component
public class LlmGateway {

    /**
     * 요청 경로에서 동시에 나갈 수 있는 LLM 호출 수.
     *
     * 5로 정한 근거: 이 값이 곧 "Gemini가 완전히 멈췄을 때 묶이는 Tomcat 요청 스레드 수"의 상한이다.
     * 요청 경로 상한이 3초이므로 최악에도 5개 스레드가 3초씩 잡히고, 기본 스레드 수(200)의 2.5%다.
     * 나머지 195개는 검색·추천처럼 LLM을 거치지 않는 요청을 계속 받는다.
     */
    private static final int INTERACTIVE_MAX_CONCURRENT = 5;

    /** 호출 수·지연·결과를 남기는 타이머 이름 */
    private static final String CALL_TIMER = "llm.call";

    /** 응답 타입마다 한 번만 만들어 두는 변환기 — 스키마 생성이 매 호출 반복될 이유가 없다 */
    private final Map<Class<?>, BeanOutputConverter<?>> converters = new ConcurrentHashMap<>();

    private final ChatClient batchChatClient;
    private final ChatClient interactiveChatClient;
    private final MeterRegistry meterRegistry;

    /** 요청 경로 격벽 — 자리가 없으면 기다리지 않고 거절한다 */
    private final Semaphore interactivePermits = new Semaphore(INTERACTIVE_MAX_CONCURRENT);

    // @Qualifier가 생성자 파라미터에 걸려야 두 ChatClient 빈이 구분되므로 수동 생성자를 쓴다.
    public LlmGateway(
        @Qualifier("chatClient") ChatClient batchChatClient,
        @Qualifier("interactiveChatClient") ChatClient interactiveChatClient,
        MeterRegistry meterRegistry
    ) {
        this.batchChatClient = batchChatClient;
        this.interactiveChatClient = interactiveChatClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 외부에서 들어온 값(상품명·재료명처럼 우리가 쓰지 않은 문자열)을 프롬프트에 끼워 넣기 전에 거친다.
     *
     * 중괄호를 지우는 이유는 Spring AI가 프롬프트 텍스트의 <code>{이름}</code>을 치환 자리로 읽기 때문이다.
     * 판매자가 상품명에 중괄호를 넣으면 우리가 의도하지 않은 자리가 생기거나 렌더링이 통째로 실패한다.
     * 재료명·상품명에 중괄호가 정당하게 들어갈 일은 없으므로 지워도 뜻이 바뀌지 않는다.
     *
     * <p>이것은 프롬프트 인젝션 방어의 전부가 아니다 — 지시문처럼 읽히는 문장을 상품명에 넣는 것은
     * 여전히 막지 못한다. 그건 프롬프트 문구를 고쳐야 하는 일이고, 골든셋 재측정이 함께 필요하다.
     */
    public static String asData(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("{", "").replace("}", "");
    }

    /**
     * 프롬프트를 보내고 응답을 {@code responseType}으로 받는다.
     *
     * @param kind         호출 경로 — 예산과 격벽이 여기서 갈린다
     * @param prompt       완성된 프롬프트. 외부 값은 {@link #asData(String)}를 거쳐 넣는다
     * @param responseType 받을 타입. 이 타입의 JSON 스키마가 Gemini에 그대로 전달된다
     * @param temperature  샘플링 온도. {@code null}이면 전역 기본값(레시피 생성 기준)을 쓴다
     * @return 파싱된 응답. 격벽 거절·호출 실패·파싱 실패는 전부 {@link Optional#empty()}
     */
    public <T> Optional<T> call(LlmCallKind kind, String prompt, Class<T> responseType, Double temperature) {
        if (kind != LlmCallKind.INTERACTIVE) {
            return timed(kind, responseType, () -> invoke(batchChatClient, prompt, responseType, temperature));
        }

        // 격벽: 자리가 없으면 호출하지 않고 즉시 돌아간다. 기다리면 요청 스레드가 쌓여 격벽이 무의미해진다.
        if (!interactivePermits.tryAcquire()) {
            record(kind, "rejected", 0L);
            log.warn("LLM 요청 경로 격벽이 가득 차 이번 호출을 건너뜁니다. (동시 상한 {})", INTERACTIVE_MAX_CONCURRENT);
            return Optional.empty();
        }
        try {
            return timed(kind, responseType, () -> invoke(interactiveChatClient, prompt, responseType, temperature));
        } finally {
            interactivePermits.release();
        }
    }

    /**
     * 실제 호출·파싱. 예외는 여기서 삼키지 않고 {@link #timed}로 올린다 — 거기서 {@code failed}로 기록한 뒤
     * 빈 결과로 바꾼다. 2026-09-09까지는 여기서 잡아 버려 타임아웃·429·파싱 실패가 전부 {@code empty}로만 남았다.
     */
    private <T> Optional<T> invoke(ChatClient client, String prompt, Class<T> responseType, Double temperature) {
        BeanOutputConverter<T> converter = converterFor(responseType);

        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder()
            .responseMimeType(MediaType.APPLICATION_JSON_VALUE)
            .responseSchema(converter.getJsonSchema());
        if (temperature != null) {
            options.temperature(temperature);
        }

        String json = client.prompt()
            .options(options.build())
            .user(u -> u.text(prompt))
            .call()
            .content();

        return Optional.ofNullable(converter.convert(json));
    }

    @SuppressWarnings("unchecked")
    private <T> BeanOutputConverter<T> converterFor(Class<T> responseType) {
        return (BeanOutputConverter<T>) converters.computeIfAbsent(responseType, BeanOutputConverter::new);
    }

    /**
     * 호출을 재고 결과 태그와 함께 남긴다. 실패는 {@code failed}로 기록한 뒤 빈 결과로 바꾼다 —
     * 호출자들이 전부 "못 만들었다"로 다루므로 예외를 올리지 않는다.
     */
    private <T> Optional<T> timed(
        LlmCallKind kind, Class<T> responseType, java.util.function.Supplier<Optional<T>> call
    ) {
        long startedAt = System.nanoTime();
        try {
            Optional<T> result = call.get();
            record(kind, result.isPresent() ? "success" : "empty", System.nanoTime() - startedAt);
            return result;
        } catch (RuntimeException e) {
            record(kind, "failed", System.nanoTime() - startedAt);
            log.warn("LLM 호출 실패 ({}): {}", responseType.getSimpleName(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void record(LlmCallKind kind, String outcome, long elapsedNanos) {
        Timer.builder(CALL_TIMER)
            .tag("kind", kind.name().toLowerCase(java.util.Locale.ROOT))
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}
