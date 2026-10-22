# Cross-cutting Architecture Audit 002 — 의존성·설정·죽은 코드 (2026-09-05)

baro-ai의 공유 인프라 — `build.gradle`, `application*.yml`, `AiApplication`, `config`·`common` 패키지, `chat` 스텁, 컨트롤러 응답 규격 — 를 감사한 결과다. 반드시 고칠 A 항목 2건(운영 지표 수집이 404로 끊겨 있음, 컨슈머 2개가 깨진 메시지 1건에 영구 정지함), 유지보수성 B 항목 8건(안 쓰는 의존성 7종·죽은 설정·로그 레벨·응답 envelope 불일치·Kafka 팩토리 중복·ES 버전 고정 해제)이 나왔고, **2026-09-05 승인 완료**(문서 끝 "승인 결과" 절). 형제 감사 3건(keyword-search/001·cart-recipe/001·vector-similarity/001)이 "공유 인프라 소관"으로 넘긴 항목은 여기서 근거를 다시 확인해 분류했다.

## 범위

- 대상: `baro-ai/build.gradle`(+ 루트 `build.gradle`·`settings.gradle`), `src/main/resources/application.yml`·`application-prod.yml`·`recipe-recommend.yml`, `src/test/resources/application-test.yml`, `com.barofarm.ai.AiApplication`, `com.barofarm.ai.config`(8개), `com.barofarm.ai.common`(`SwaggerConfig`·`GlobalExceptionHandler`), `com.barofarm.ai.chat`(스텁 4개), 응답 규격 확인용 컨트롤러 3개(`SearchController`·`RecommendationController`·`ReviewBatchController` — 마지막은 읽기만), `baro-common`의 `ResponseDto`·`CustomPage`·`BaseExceptionHandler`·자동설정, `k8s/apps/baro-ai/*.yml`(읽기만)
- 감사자: 메인 세션이 넘긴 가설 11개를 신선한 컨텍스트에서 코드·빌드 파일·jar로 독립 검증했다 — `safe-refactor` 1단계. 의존성 사실은 `./gradlew :baro-ai:dependencies --configuration runtimeClasspath` 출력(770줄, 이하 "의존성 보고서")으로, 자동설정 조건은 gradle 캐시의 jar를 `javap`으로 열어 확인했다
- 기준: `audit-checklist.md` 1~16항목(이번 라운드는 2·3·9·12·13번이 중심, 16번 LLM(Large Language Model, 거대 언어 모델)은 [`001`](001-llm-call-infra.md)에서 끝나 제외), `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- **제외**: LLM 호출 인프라(`LlmClientConfig`·`spring.ai.*`)는 [`001`](001-llm-call-infra.md)에서 반영 완료 — 재감사하지 않는다. `ElasticsearchConfig` 삭제와 `spring.elasticsearch.*` 타임아웃은 [`vector-similarity/001` A-2](../vector-similarity/001-vector-search-and-diversity.md)(= [`keyword-search/001` B-2](../keyword-search/001-query-path.md))가 소유한다. **review 전용 설정**(`NegativeReviewPropertiesConfig`·`SentimentPropertiesConfig`·`KafkaProducerConfig`·`reviewEventConsumerFactory`)과 review 컨트롤러는 팀원 소유라 A/B에 넣지 않고 "판단이 서지 않는 항목"으로 보냈다. 단위 테스트 관련 항목은 2026-09-04 사용자 결정으로 제외. `k8s/` 파일은 이 하네스 쓰기 범위 밖이라 "배포 담당자가 할 일"로만 적는다
- **형제 감사에서 넘어온 항목과 이 문서의 대응**

| 형제 감사 항목 | 이 문서 |
|----------------|---------|
| keyword-search/001 C-2 (응답 envelope 불일치) | B-5 |
| keyword-search/001 C-3 (prod DEBUG 로그) | B-4 (+ 판단 항목 2) |
| keyword-search/001 C-4 (죽은 `spring.kafka.consumer.*`) | B-6 (+ 판단 항목 3) |
| cart-recipe/001 C-4 (안 쓰는 의존성 후보) | B-1·B-2·B-7 — 단 `feign-okhttp`는 결론이 **반대**(B-7 참고) |
| vector-similarity/001 C-2 (ES 클라이언트 버전 고정) | B-8 |
| 001 C-2 (`management.*` 블록) | A-1 — actuator는 이미 붙었고, 이제 문제는 노출 범위다 |

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 운영 지표 수집(ServiceMonitor → `/actuator/prometheus`)이 404로 끊겨 있음

- **Priority**: High
- **Category**: Architecture (Monitoring)
- **문제**: 배포 매니페스트는 30초마다 `/actuator/prometheus`를 긁어 가도록 돼 있는데, baro-ai는 그 엔드포인트를 열어 두지 않았다. Prometheus 레지스트리 자체는 `baro-common`을 통해 classpath에 올라와 있어 엔드포인트 빈은 만들어지지만, `management.endpoints.web.exposure.include: health,metrics`에 `prometheus`가 없어 HTTP로는 404다. 결과적으로 001 B-1로 붙인 LLM 토큰·지연 지표를 포함해 **어떤 지표도 모니터링 스택에 도달하지 않는다**. 같은 저장소의 다른 서비스는 전부 `metrics, prometheus`를 연다
- **근거**:
  - `k8s/apps/baro-ai/servicemonitor.yml:16-18` — `path: /actuator/prometheus`, `interval: 30s`
  - `application-prod.yml:88-92` — `include: health,metrics` (2026-09-04 001 B-1 반영 때 `*`에서 좁힌 값 — 그 시점엔 Prometheus 수집을 확인하지 않았다)
  - 레지스트리는 존재한다: `baro-common/build.gradle:44` `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` → 의존성 보고서 496줄 `project :baro-common` 하위 `io.micrometer:micrometer-registry-prometheus -> 1.15.6`. `spring-boot-actuator-autoconfigure-3.5.8.jar`의 `PrometheusMetricsExportAutoConfiguration`은 `@ConditionalOnClass(PrometheusMeterRegistry)` + `@ConditionalOnEnabledMetricsExport("prometheus")`(기본 켜짐)이고, `spring-boot-actuator-3.5.8.jar`의 `PrometheusScrapeEndpoint`는 `@WebEndpoint(id = "prometheus")`다 — 즉 빈은 뜨고 노출만 막혀 있다
  - 저장소 관례: `baro-order/.../application-prod.yml:59`·`baro-shopping/.../application-prod.yml:59`·`baro-settlement`·`baro-payment`·`baro-notification` 전부 `include: refresh, health, beans, info, metrics, prometheus`, `baro-gateway/.../application-prod.yml:144` `health, info, gateway, metrics, prometheus`
  - 트레이싱 쪽은 반대로 이미 연결돼 있다: `deployment.yml:42-45`이 `MANAGEMENT_OTLP_TRACING_ENDPOINT`·`MANAGEMENT_TRACING_SAMPLING_PROBABILITY`를 주입하고, `baro-common/build.gradle:45-46`의 `micrometer-tracing-bridge-otel`·`opentelemetry-exporter-otlp`가 의존성 보고서 442·469줄에 있다. 지표만 빠져 있다
- **왜 문제인가**: 프로덕션에서 "느린지·실패하는지"를 볼 유일한 창이 막혀 있다. 이 이니셔티브의 다른 항목들(재시도 값 조정, 게이트웨이 5초 예산 — cart-recipe/001 판단 항목 1)이 전부 "지표를 보고 정한다"를 전제로 하는데, 그 지표가 클러스터에 도착하지 않는다
- **개선 방법**: `application-prod.yml:92`를 `include: health,metrics,prometheus`로 바꾼다. 다른 모듈처럼 `beans`·`env`까지 열지는 않는다(001 B-1이 노출 범위를 의도적으로 좁혔다 — 그 결정은 유지). 배포 담당자가 할 일은 없다 — ServiceMonitor는 이미 맞는 경로를 보고 있다
- **검증 방법** (로컬 수동): `SPRING_PROFILES_ACTIVE=prod`로 기동 후 `curl -i localhost:8092/actuator/prometheus`. **Before**: `404`. **After**: `200` + `gen_ai_client_token_usage_total{...}`·`jvm_memory_used_bytes{...}` 같은 텍스트 지표. 추천 API를 1회 호출한 뒤 다시 긁으면 `gen_ai_client_operation_duration_seconds_count`가 1 증가한다
- **API 영향**: No Impact (actuator 경로는 게이트웨이 라우트 밖)
- **예상 변경 파일**: `src/main/resources/application-prod.yml`
- **예상 변경 라인 수**: 1
- **위험도**: Low — 노출 범위가 지표 텍스트 하나 늘어난다. 게이트웨이 밖으로 열리는지는 001 B-1과 같은 전제(배포 설정에서 확인)
- **테스트 영향도**: 없음
- **예상 효과**: 운영 지표가 실제로 수집됨 · 001 B-1의 효과가 로컬 curl 밖에서도 살아남

### A-2. cart·order 컨슈머가 역직렬화에 실패한 메시지 1건에 영구 정지함

> **SSOT 분담**: 같은 결함을 [`taste-vector/001`](../taste-vector/001-profile-refresh-path.md) A-5가 컨슈머 동작 관점(재시도 백오프·중복 로그·결정적 문서 ID)까지 넓게 다룬다. 이 항목은 `KafkaConsumerConfig`의 팩토리 코드 변경(래핑, B-6 공통 메서드)만 소유하고, 구현 시 A-5의 백오프·ID 결정을 같은 변경에 반영한다.

- **Priority**: Medium
- **Category**: Architecture (Resilience)
- **문제**: `productEventConsumerFactory`만 `ErrorHandlingDeserializer`로 감싸고, `cart`·`order`(·`review`) 팩토리는 `JsonDeserializer`를 그대로 쓴다. 깨진 JSON이나 스키마가 안 맞는 메시지가 한 건 들어오면 `poll()` 자체가 예외를 던지고, 컨슈머는 offset을 넘기지 못한 채 같은 레코드를 무한히 다시 받는다. 그 뒤로 도착하는 정상 이벤트는 전부 처리되지 않는다 — 취향 벡터 파이프라인이 조용히 멈춘다
- **근거**:
  - `KafkaConsumerConfig.java:75-78` — product만 `ErrorHandlingDeserializer` + `setFailedDeserializationFunction(record -> null)`. `:109-114`(cart)·`:133-138`(order)·`:156-161`(review)는 `new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer)`에 날 `JsonDeserializer`를 넘긴다
  - `kafka-clients-3.9.1.jar` — `RecordDeserializationException extends SerializationException`(javap). 역직렬화 실패는 리스너가 아니라 `poll()`에서 터진다
  - `spring-kafka-3.3.11.jar` `DefaultErrorHandler` 문자열: *"This error handler cannot process 'SerializationException's directly; please consider configuring an 'ErrorHandlingDeserializer' in the value and/or key deserializer"* — 컨테이너 기본 에러 핸들러가 이 예외를 받으면 그 문장을 던지고 offset은 그대로다. 같은 jar의 `ExceptionClassifier`는 `DeserializationException`(ErrorHandlingDeserializer가 헤더로 바꿔 준 형태)을 재시도 불가로 분류해 건너뛴다 — 즉 감싸기만 하면 기본 핸들러가 올바르게 처리한다
  - 5인 저장소에서 이벤트 모델은 발행 모듈(`baro-common` `HistoryEnvelope`·`CartEventData`)과 소비 모델(`CartLogEvent`·`OrderLogEvent`)이 따로 정의돼 있어 어긋날 여지가 실제로 있다(`docs/specs/taste-vector/taste-vector-pipeline.md` 흐름 표)
  - **불확실한 것**: 실제 poison 메시지가 발생한 기록은 없다(운영 서버 내려감). 위 사슬은 코드·jar로 닫혀 있다
- **왜 문제인가**: 장애가 "에러 로그 폭주 + 파이프라인 정지"인데, 원인 레코드를 수동으로 건너뛰기 전까지 스스로 복구되지 않는다. product 팩토리에는 이미 같은 이유로 방어를 넣었고(주석 `:75-76`), 다른 세 개만 빠진 상태다
- **개선 방법**: cart·order 팩토리의 `JsonDeserializer`를 `ErrorHandlingDeserializer`로 감싼다. product와 달리 `setFailedDeserializationFunction`은 **두지 않는다** — null을 리스너에 넘기면 `CartEventConsumer.java:31`·`OrderEventConsumer`가 `event.payload()`에서 NPE를 내므로 리스너까지 고쳐야 한다. 함수 없이 감싸면 컨테이너의 `DefaultErrorHandler`가 `DeserializationException` 헤더를 보고 로그 후 건너뛰고, 리스너 코드는 그대로다. B-6의 공통 팩토리 메서드에 이 감싸기를 넣으면 세 팩토리가 한 번에 바뀐다 — **review 팩토리도 같은 메서드를 타므로 판단 항목 4로 사용자 확인**
- **검증 방법** (로컬 수동): 로컬 Kafka에 `echo 'not-json' | kafka-console-producer --topic cart-events --bootstrap-server localhost:9092`로 깨진 레코드 1건, 이어서 정상 `CartLogEvent` JSON 1건을 넣는다. **Before**: `SerializationException` 로그가 반복되고 두 번째 정상 이벤트의 `[CART_CONSUMER] Received cart event` 로그가 안 찍힌다. **After**: 첫 건에 `DeserializationException` 로그 1회(건너뜀), 두 번째 건은 정상 처리 로그가 찍힌다
- **API 영향**: No Impact
- **예상 변경 파일**: `config/KafkaConsumerConfig.java`
- **예상 변경 라인 수**: +6 (B-6과 함께 하면 공통 메서드 안 2줄)
- **위험도**: Low — 정상 메시지 경로는 바뀌지 않는다(ErrorHandlingDeserializer는 성공 시 위임 결과를 그대로 돌려준다)
- **테스트 영향도**: 없음
- **예상 효과**: 잘못된 메시지 1건이 파이프라인 전체를 멈추지 못함

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. JPA 스타터·MySQL 드라이버·H2 테스트 설정 — baro-ai에는 쓰는 곳이 없다 (H1)

- **Priority**: Medium
- **Category**: Dead Code (의존성)
- **문제**: `spring-boot-starter-data-jpa`·`mysql-connector-j`를 선언하고, prod에서는 DataSource·Hibernate 자동설정을 제외해 끄고, 테스트 리소스에는 H2 설정을 둔다. baro-ai 코드에는 JPA 엔티티·리포지토리가 한 줄도 없고 테스트 클래스도 0개다
- **근거**:
  - `build.gradle:62` `spring-boot-starter-data-jpa`, `:91` `runtimeOnly 'com.mysql:mysql-connector-j'`. `grep -rn 'jakarta.persistence\|@Entity\|JpaRepository\|EnableJpa' baro-ai/src/main/java` **0건**
  - `application-prod.yml:17-20` — `DataSourceAutoConfiguration`·`HibernateJpaAutoConfiguration` 제외
  - `src/test/resources/application-test.yml:5-24` — `datasource`·`jpa`·`h2` 블록이 `spring:`이 아니라 `api:` 아래에 들여쓰기돼 있어 `api.datasource.*`로 읽힌다. 바인딩되는 곳이 없는 설정이고, 읽을 테스트도 없다(`src/test/java` 없음). 생성 커밋 `d48eb893`(#98 모듈 기본 생성) 이후 변경 이력 없음
  - **단, Hibernate 자체는 이 변경으로 사라지지 않는다** — `baro-common/build.gradle:27`이 `implementation 'spring-boot-starter-data-jpa'`를 선언하고(`BaseEntity`·`JpaAuditingConfig`용), baro-ai는 `implementation project(':baro-common')`라 의존성 보고서 115줄에서 baro-common 하위로 JPA가 런타임 classpath에 남는다. `JpaAuditingConfig`(`@EnableJpaAuditing`)는 자동설정 imports(`baro-common/.../AutoConfiguration.imports` 4개)에 없고 baro-ai 컴포넌트 스캔(`com.barofarm.ai`) 밖이라 활성화되지 않는다. 따라서 **`autoconfigure.exclude` 2줄은 그대로 둬야 한다** — 지우면 baro-common이 끌어온 Hibernate 자동설정이 DataSource를 찾다 기동에 실패한다
  - `mysql-connector-j`는 baro-ai 최상위 선언뿐이다(의존성 보고서 761줄, baro-common 하위에는 없음) — 이건 지우면 실제로 classpath에서 빠진다
- **왜 문제인가**: 읽는 사람이 "이 모듈이 MySQL도 쓰나?"를 매번 확인해야 하고, `application-test.yml`은 H2 프로필이 있는 것처럼 보이지만 키가 잘못 중첩돼 있어 어차피 동작하지 않는다. `harness-map.md` 스택 옵션(RDB 없음)과 빌드 파일이 서로 다른 말을 한다
- **개선 방법**: `build.gradle:62`·`:91` 삭제, `src/test/resources/application-test.yml` 삭제(테스트를 들이는 날 `testing.md` 기준으로 새로 만든다 — 001 C-3). `application-prod.yml:17-20`은 **유지**하고 주석에 "baro-common이 JPA를 끌어오므로 제외 필요"를 한 줄 남긴다. `docs/README.md:183-192`의 패키지 트리(`persistence/jpa/`)는 C-4에서 다룬다
- **검증 방법**: `./gradlew :baro-ai:compileJava` 통과 + `./gradlew :baro-ai:dependencies --configuration runtimeClasspath | grep mysql` 0건. 기동 확인은 로컬 인프라(ES·Kafka·Gemini 키)가 필요해 사용자가 `SPRING_PROFILES_ACTIVE=prod`로 `bootRun` 후 `/actuator/health`가 `UP`인지 본다
- **API 영향**: No Impact
- **예상 변경 파일**: `build.gradle`, `src/main/resources/application-prod.yml`(주석 1줄), `src/test/resources/application-test.yml`(삭제)
- **예상 변경 라인 수**: −2 (gradle), +1 (yml 주석), −24 (test yml)
- **위험도**: Low
- **테스트 영향도**: 없음(테스트 0개)
- **예상 효과**: 빌드 파일이 실제 스택과 일치 · MySQL 드라이버(+protobuf) classpath 제거

### B-2. Config Server·Eureka 클라이언트 — prod에서 꺼져 있고 로컬 기동을 막는다 (H2)

- **Priority**: Medium
- **Category**: Dead Code (의존성) / Spring
- **문제**: `spring-cloud-starter-config`와 `spring-cloud-starter-netflix-eureka-client`(+`@EnableDiscoveryClient`)를 선언하지만, 배포는 둘 다 환경변수·yml로 끄고 있다. Config 클라이언트는 꺼 두지 않으면 기동을 거부하는 성격이라, 배포 매니페스트에 그걸 끄는 환경변수 2개가 따로 들어가 있고 로컬 실행 안내에는 그게 없다
- **근거**:
  - `build.gradle:64-65`. `AiApplication.java:9` `@EnableDiscoveryClient`
  - Eureka: `application-prod.yml:58-60` `eureka.client.enabled: false`. `cart-recipe/001` A-2가 Feign을 명시 `url`로 바꾸면 서비스 이름 해석이 필요한 코드가 없어진다. `EnableDiscoveryClient`는 저장소 전 모듈이 붙이고 있지만(9개) baro-ai에서 그 어노테이션을 요구하는 코드는 없다
  - Config: `k8s/apps/baro-ai/deployment.yml:33-36` `SPRING_CLOUD_CONFIG_ENABLED=false`·`SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED=false`. `spring-cloud-config-client-4.3.0.jar`의 `ConfigServerConfigDataMissingEnvironmentPostProcessor`는 `spring.cloud.config.enabled`·`spring.cloud.config.import-check.enabled`·`configserver:` 문자열을 검사하고, 상위 `spring-cloud-commons-4.3.0.jar` `ConfigDataMissingEnvironmentPostProcessor`는 *"No spring.config.import set"* 예외를 던진다. baro-ai yml에는 `configserver:` import가 없다(`grep -rn configserver baro-ai/src/main/resources` 0건 — `application-prod.yml:10-14`의 import는 classpath yml 3개뿐). 즉 **k8s 환경변수 2개가 없는 환경(로컬)에서는 기동 시 `ImportException`이 난다**. `docs/IMPLEMENTATION.md:89-92`의 로컬 실행 안내는 프로필·Gemini 키·ES·Kafka 4개만 export한다
  - 저장소의 다른 모듈은 `spring.config.import: ${SPRING_CONFIG_IMPORT:optional:configserver:http://...}`를 yml에 두어 이 검사를 통과시킨다(`baro-shopping/.../application-prod.yml:13` 등). baro-ai만 환경변수로 끈다
  - 로드밸런서: 의존성 보고서 564줄 — `spring-cloud-starter-loadbalancer`는 **Eureka 스타터 하위로만** 들어온다(`spring-cloud-starter-openfeign` 하위에는 없음, 405-412줄). Eureka를 빼면 `url` 없는 `@FeignClient`는 기동 시 "No Feign Client for loadBalancing defined"로 실패하므로 **cart-recipe/001 A-2(명시 url)가 먼저**다
  - 배경: 둘 다 생성 커밋 `d48eb893`(#98)의 모듈 템플릿에서 왔다
- **왜 문제인가**: "왜 이 서비스만 로컬에서 안 뜨지"를 겪는 사람이 config 클라이언트의 존재를 알아내야 한다. 배포 파일에 서비스가 쓰지도 않는 기능을 끄는 항목이 남아 있어 읽는 사람을 헷갈리게 한다
- **개선 방법**: (1) cart-recipe/001 A-2 반영 확인 → (2) `build.gradle:64-65` 삭제, `AiApplication.java:9`와 import 삭제, `application-prod.yml:58-60` 삭제 → (3) `docs/IMPLEMENTATION.md` 로컬 실행 절에 "config server 없음" 한 줄. k8s의 `SPRING_CLOUD_CONFIG_*` 환경변수 2개는 지워도 무해하지만 이 하네스 범위 밖이므로 배포 담당자에게 전달만 한다(판단 항목 10)
- **검증 방법**: `./gradlew :baro-ai:compileJava` 통과. 기동 확인은 `SPRING_CLOUD_CONFIG_ENABLED`·`SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED`를 **주지 않고** `SPRING_PROFILES_ACTIVE=prod bootRun` — **Before**: `No spring.config.import set` 계열 `ImportException`으로 기동 실패. **After**: 기동. 이어서 추천 API 1회로 Feign이 명시 url로 나가는지(A-2 검증 절차) 확인
- **API 영향**: No Impact
- **예상 변경 파일**: `build.gradle`, `AiApplication.java`, `src/main/resources/application-prod.yml`, `docs/IMPLEMENTATION.md`
- **예상 변경 라인 수**: −2, −2, −3, +1
- **위험도**: Medium — cart-recipe A-2 순서를 지키지 않으면 Feign 빈 생성이 실패한다. 순서만 지키면 Low
- **테스트 영향도**: 없음
- **예상 효과**: 로컬 기동 함정 제거 · Eureka·Config·LoadBalancer 계열 의존성 제거

### B-3. `spring.main.allow-bean-definition-overriding: true` — 덮어쓰는 빈이 없고, 앞으로의 실수를 숨긴다 (H5)

- **Priority**: Medium
- **Category**: Spring
- **문제**: 같은 이름의 빈이 두 번 정의될 때 나중 것이 조용히 이기도록 하는 설정이다. Spring Boot가 2.1부터 기본으로 끈 이유는 자동설정 빈이 사용자 빈을 덮어쓰거나 그 반대가 되는 걸 **기동 시점에 잡기 위해서**다. baro-ai에서 실제로 충돌하는 빈 이름을 찾지 못했다
- **근거**:
  - `application-prod.yml:16`. 도입 커밋 `f0cd09dc`(#107 배포) — 저장소 전 모듈이 같은 줄을 갖고 있어(9개 yml) 템플릿 복사로 보인다
  - 사용자 빈과 자동설정 빈의 이름·타입이 겹칠 수 있는 곳을 jar로 확인한 결과 전부 `@ConditionalOnMissingBean`이라 **덮어쓰기가 아니라 자동설정 쪽이 물러난다**: `googleGenAiClient`(`spring-ai-autoconfigure-model-google-genai-1.1.8.jar` `GoogleGenAiChatAutoConfiguration.googleGenAiClient` — `@Bean @ConditionalOnMissingBean`, `LlmClientConfig.java:40`와 이름이 같음), `RestClient`(`vector-similarity/001` A-2 근거 — Boot `elasticsearchRestClient`는 `@ConditionalOnMissingBean`), Kafka 3종(`spring-boot-autoconfigure-3.5.8.jar` `KafkaAutoConfiguration.kafkaConsumerFactory` `@ConditionalOnMissingBean(ConsumerFactory)`, `kafkaProducerFactory` `@ConditionalOnMissingBean(ProducerFactory)`, `kafkaTemplate` `@ConditionalOnMissingBean(KafkaTemplate)`). `openAPI`는 springdoc이 정의하지 않고 `Optional<OpenAPI>`로 주입받기만 한다(`springdoc-openapi-starter-common-2.8.5.jar` `SpringDocConfiguration.openAPIBuilder`). `chatClient`·`profileUpdateExecutor`·`*ListenerContainerFactory`는 자동설정에 같은 이름이 없다
  - 이 설정이 "필요했던" 흔적은 없다 — 도입 커밋에 이유 주석이 없고, 001의 `googleGenAiClient` 추가 때도 충돌하지 않았다(위 근거)
- **왜 문제인가**: 지금은 무해하지만, 다음에 누군가 자동설정과 같은 이름의 빈을 조건 없이 만들면(예: `@Bean KafkaTemplate kafkaTemplate()`) 어느 쪽이 살아남는지 로그 한 줄(`Overriding bean definition ...`, INFO)로만 알 수 있다. 001 A-1처럼 "설정을 넣었는데 실제로는 다른 빈이 쓰이고 있었다"류 사고를 기동 실패로 바꿔 주는 안전장치를 꺼 둔 상태다
- **개선 방법**: `application-prod.yml:16` 삭제(`spring.main` 블록이 비면 블록째). `vector-similarity/001` A-2로 `ElasticsearchConfig`를 지우기 전에 해도 된다 — 그 빈도 위 근거대로 충돌이 아니다
- **검증 방법**: `SPRING_PROFILES_ACTIVE=prod bootRun`이 `BeanDefinitionOverrideException` 없이 뜨는지. 보조 확인: 지우기 **전** 기동 로그에서 `grep "Overriding bean definition"` — 0줄이면 덮어쓰기가 없었다는 직접 증거
- **API 영향**: No Impact
- **예상 변경 파일**: `src/main/resources/application-prod.yml`
- **예상 변경 라인 수**: −2
- **위험도**: Low — 충돌이 있다면 기동이 실패해 즉시 드러난다(그게 목적)
- **테스트 영향도**: 없음
- **예상 효과**: 빈 이름 충돌을 기동 시점에 감지

### B-4. prod 로그 레벨 — 프레임워크 3개 패키지가 DEBUG (H6)

- **Priority**: Medium
- **Category**: Performance (로그 I/O) / Security
- **문제**: 운영 프로필이 `org.springframework.web`·`DispatcherServlet`·`org.elasticsearch`를 DEBUG로 켠다. 요청 1건마다 프레임워크 로그가 최소 4~5줄 붙고, 응답 본문 일부가 로그에 남는다
- **근거**:
  - `application-prod.yml:67-70`. `:68`의 `DispatcherServlet` 줄은 `:67`의 `org.springframework.web`에 이미 포함돼 있어 중복이다
  - keyword-search/001 C-3이 jar 문자열로 확인한 줄 수: `DispatcherServlet` DEBUG는 요청당 2줄(`GET /api/v1/search…`·`Completed 200 OK`), `org.elasticsearch.client.RestClient` DEBUG는 ES 호출당 1줄. 여기에 `spring-webmvc-6.2.14.jar` `AbstractMessageConverterMethodProcessor`의 `Writing [`(응답 본문 앞부분을 DEBUG로 기록) 1줄이 더해진다 — 검색 1회 = 프레임워크 로그 최소 4줄 + `com.barofarm.ai` DEBUG
  - 응답 본문에는 개인화 추천 결과·장바구니 재료(사용자 식별 가능)가 들어간다(`RecommendationController.java:38-44,64-68`) — `Writing [` 줄로 그 앞부분이 로그에 남는다
  - 로그 출력은 `baro-common/src/main/resources/logback-spring.xml`(baro-ai에 자체 logback 없음)이 콘솔 + `logs/*.log` 파일로 보낸다 — 파일 appender는 WARN/ERROR만 받지만 콘솔(컨테이너 stdout)은 전부 받는다
  - 저장소 관례: `baro-order` prod도 `com.barofarm.order`·`org.hibernate.SQL`을 DEBUG로 두지만 `org.springframework.web`은 켜지 않는다(`baro-order/.../application-prod.yml:90-95`)
- **왜 문제인가**: 로그 양은 곧 stdout 처리 비용과 저장 비용이고, 응답 본문이 로그에 남는 건 사용자 데이터가 로그 파이프라인으로 흘러가는 경로다. 디버깅에 필요하면 `com.barofarm.ai: DEBUG`(자체 코드)와 A-1의 지표로 충분하다
- **개선 방법**: `:67-68`·`:70` 삭제 → `org.springframework.web`·`org.elasticsearch`는 root의 INFO를 따른다. `com.barofarm.ai: DEBUG`(`:66`)와 `org.springframework.kafka: INFO`(`:69`)는 유지. `org.springframework.ai: DEBUG`(`:71`)는 **판단 항목 2** — 지금 LLM 요청 본문을 들여다보는 유일한 수단이다. 이 파일은 review 컨트롤러·컨슈머 로그에도 적용되므로 반영 시 review 팀에 알린다(review 코드·설정 변경은 아니다 — 프레임워크 패키지 레벨만 바뀐다)
- **검증 방법** (로컬 수동): `SPRING_PROFILES_ACTIVE=prod`로 기동해 `curl 'localhost:8092/api/v1/search?q=토마토'` 1회 후 그 요청의 로그 줄 수를 센다. **Before**: `o.s.web.servlet.DispatcherServlet`·`o.e.client.RestClient`·`Writing [` 줄이 보인다. **After**: 그 줄들이 없고 `com.barofarm.ai` 줄만 남는다
- **API 영향**: No Impact
- **예상 변경 파일**: `src/main/resources/application-prod.yml`
- **예상 변경 라인 수**: −3
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 요청당 로그 4줄 이상 감소 · 응답 본문의 로그 유출 경로 제거

### B-5. 추천 API 응답이 `ResponseDto` envelope 없이 나가고, 에러 응답은 envelope로 나간다 (H7)

- **Priority**: Medium
- **Category**: Architecture (API 계약)
- **문제**: `SearchController`는 `ResponseDto<…>`로 감싸고 `RecommendationController`는 `List`·record를 그대로 돌려준다. 그런데 예외는 두 컨트롤러 모두 `BaseExceptionHandler`가 `ResponseDto.error(...)`로 감싸 보내므로, **같은 추천 엔드포인트가 성공 시엔 날 배열, 실패 시엔 `{status,data,message}`**를 준다. 호출자는 응답 모양으로 성공·실패를 구분할 수 없다
- **근거**:
  - `SearchController.java:35,51` — `ResponseDto<CustomPage<…>>`·`ResponseDto<List<…>>`. `RecommendationController.java:38,52,64,76` — `List<ProductRecommendResponse>`·`RecipeRecommendResponse` 직접 반환
  - `common/exception/GlobalExceptionHandler.java:6-8`이 `baro-common` `BaseExceptionHandler`를 그대로 상속하고, 그 핸들러는 전부 `ResponseEntity<ResponseDto<Void>>`(`BaseExceptionHandler.java:29-43,49-68` 등)
  - baro-ai 자체 규약: `AGENTS.md` Conventions "API 응답은 `baro-common`의 `ResponseDto`/`CustomPage` 사용". 스펙은 현행을 사실대로 적어 두었다 — `keyword-search/product-search-and-autocomplete.md:50` "추천 API는 감싸지 않는다", `taste-vector/taste-vector-pipeline.md:53` "**envelope 없이** … 검색 API가 `ResponseDto`로 감싸는 것과 다르다"
  - 저장소 관례(컨트롤러 파일 기준): `baro-shopping` 6개 중 5개·`baro-payment` 3/3·`baro-order` 4/6이 `ResponseDto`를 쓴다. baro-ai가 Feign으로 호출하는 `CartController`도 `ResponseDto<CartInfo>`다(`cart-recipe/001` A-1이 그 envelope를 못 벗겨 생긴 버그를 다룬다). `baro-user`·`baro-notification`은 날 반환이 많아 저장소 전체가 통일돼 있지는 않다 — 그래서 근거는 "다른 모듈이 그렇게 해서"가 아니라 **baro-ai 안에서 성공·실패 모양이 다르다**는 사실이다
  - `baro-ai-performance-refactor.md` API 계약 변경 방침: 릴리즈 전이라 더 명확해지는 방향이면 계약을 바꿀 수 있다. 호출하는 클라이언트는 없다(`docs/product/platform.md`)
- **왜 문제인가**: 호출자가 `status` 필드 유무로 분기하거나, 배열인지 객체인지 먼저 봐야 한다. 앞으로 프론트·게이트웨이가 붙을 때 baro-ai만 예외 처리 코드가 달라진다
- **개선 방법**: `RecommendationController`의 4개 메서드 반환 타입을 `ResponseDto<…>`로 바꾸고 `ResponseDto.ok(...)`로 감싼다(`SearchController`와 같은 스타일 — `ResponseEntity`는 baro-ai에서 쓰지 않는다). 스펙 3개(`taste-vector:53`·`vector-similarity`·`cart-recipe`의 응답 절)와 `keyword-search:50`의 "감싸지 않는다" 문구를 같은 변경에서 amend한다(STOP §4 — '현행' 문구). `POST /recipes/test`는 `cart-recipe/001` B-2가 삭제 후보라 그쪽 결정을 따른다. `ReviewBatchController`(`Map` 반환)는 review 소유 — 판단 항목 7
- **검증 방법**: 기동 후 `curl localhost:8092/api/v1/recommendations/similar/{uuid}` — **After**: `{"status":200,"data":[…],"message":null}`. 존재하지 않는 경로로 404를 내면 Before/After 모두 `{"status":404,…}`라 성공·실패 모양이 같아졌는지 눈으로 비교된다. `/v3/api-docs`에서 `ResponseDto…` 스키마가 4개 경로에 잡히는지 확인(`harness-map.md` 생성 문서 검증 옵션)
- **API 영향**: **Breaking** — 5인 저장소라 `refactor-log.md`에 아래 표를 그대로 남긴다

| 엔드포인트 | Before (성공) | After (성공) | 실패 (변화 없음) |
|-----------|---------------|--------------|------------------|
| `GET /api/v1/recommendations/personalized/{userId}` | `[ {productId,…}, … ]` | `{ "status":200, "data":[ {productId,…}, … ], "message":null }` | `{ "status":4xx/5xx, "data":null, "message":"…" }` |
| `GET /api/v1/recommendations/similar/{productId}` | `[ … ]` | `{ "status":200, "data":[ … ], "message":null }` | 동일 |
| `GET /api/v1/recommendations/recipes/{userId}` | `{ "recipes":…, … }` | `{ "status":200, "data":{ "recipes":…, … }, "message":null }` | 동일 |
| `POST /api/v1/recommendations/recipes/test` | `{ … }` | cart-recipe/001 B-2 결정에 따름(삭제 시 해당 없음) | 동일 |

- **예상 변경 파일**: `recommend/presentation/RecommendationController.java`, `docs/specs/taste-vector/taste-vector-pipeline.md`, `docs/specs/vector-similarity/vector-similarity-search.md`, `docs/specs/cart-recipe/recipe-recommendation.md`, `docs/specs/keyword-search/product-search-and-autocomplete.md`, `docs/FEATURES.md`(응답 예시가 있으면)
- **예상 변경 라인 수**: Java ±10, 문서 ±12
- **위험도**: Low — 호출 클라이언트 없음. 다른 모듈이 이 API를 Feign으로 부르는 곳도 없다(`docs/specs/README.md` 표: baro-ai 추천 API를 호출하는 모듈 없음)
- **테스트 영향도**: 없음
- **예상 효과**: 성공·실패 응답 모양 일치 · baro-ai 규약(`AGENTS.md`)과 코드 일치

### B-6. `KafkaConsumerConfig` — 같은 팩토리 코드 4벌, 효과 없는 신뢰 패키지 설정, 죽은 yml 키 (H4)

- **Priority**: Medium
- **Category**: Readability / Dead Code
- **문제**: 컨슈머 팩토리 4개가 타입·그룹 접미사·offset 초기값만 다르고 나머지 12줄이 같다. 각 팩토리의 `addTrustedPackages(...)`는 `setUseTypeHeaders(false)` 때문에 읽히지 않고, product 팩토리의 주석("메시지 본문의 `@class` 필드 처리용", "와일드카드가 작동하지 않을 수 있으므로")은 둘 다 사실과 다르다. `application-prod.yml`의 `spring.kafka.consumer.{key,value}-deserializer`·`auto-offset-reset`은 아무 리스너도 쓰지 않는 Boot 기본 팩토리만 읽는다
- **근거**:
  - 중복: `KafkaConsumerConfig.java:101-115`(cart)·`:125-139`(order)·`:149-162`(review)는 타입·`groupId + "-xxx"`·`"earliest"`만 다르다. product(`:53-81`)는 여기에 `MAX_POLL_RECORDS`와 `ErrorHandlingDeserializer`가 추가된다. 컨테이너 팩토리 4개(`:83-97`,`:117-123`,`:141-147`,`:164-170`)도 product의 에러 핸들러만 다르다. 파일 171줄
  - 신뢰 패키지가 죽어 있음: `spring-kafka-3.3.11.jar` `JsonDeserializer.setUseTypeHeaders(false)` → `setUpTypePrecedence(emptyMap)` → `typeMapper.setTypePrecedence(INFERRED)`(javap). `deserialize(topic, headers, data)`는 `typePrecedence == TYPE_ID`일 때만 `typeMapper.toJavaType(headers)`(여기서 신뢰 패키지 검사)를 타고, 아니면 생성자에 준 `targetType`으로 바로 읽는다. 본문의 `@class` 필드는 `JsonDeserializer`가 아예 보지 않는다(Jackson 기본 타이핑 미사용). 또 `DefaultJackson2JavaTypeMapper.addTrustedPackages`는 `"*"`를 만나면 목록을 비우고 전부 신뢰한다(javap `ldc "*"` → `Set.clear`) — "와일드카드가 안 될 수 있다"는 주석(`:67`)과 반대다
  - 그룹 ID 이중 정의: `ProductEventConsumer.java:20` `groupId = "search-service"`가 팩토리의 `GROUP_ID_CONFIG`(`ai-service`)를 덮어쓴다(어노테이션 값이 우선). cart·order·review는 어노테이션 값과 팩토리 값이 우연히 같다
  - 죽은 yml: `application-prod.yml:29-33`. `spring-boot-autoconfigure-3.5.8.jar` `KafkaAutoConfiguration.kafkaConsumerFactory`는 `@ConditionalOnMissingBean(ConsumerFactory)`라 커스텀 팩토리 4개가 있으면 만들어지지 않는다. 다만 `KafkaAnnotationDrivenConfiguration.kafkaListenerContainerFactory`는 `@ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")`(이름 조건)라 여전히 생성되고, 이 빈만 yml 값으로 자체 `DefaultKafkaConsumerFactory`를 만든다 — 그러나 리스너 4개가 전부 `containerFactory`를 명시한다(`ProductEventConsumer.java:21`·`CartEventConsumer.java:28`·`OrderEventConsumer.java:29`·`ReviewConsumer.java:18`). `group-id`(`:30`)와 `bootstrap-servers`(`:28`)만 `@Value`로 실제 읽힌다(`KafkaConsumerConfig.java:34-38`)
  - `@EnableKafka`가 `KafkaConsumerConfig.java:31`·`KafkaProducerConfig.java:18` 두 곳에 있다 — 한 곳이면 된다
- **왜 문제인가**: A-2 같은 방어를 넣을 때 네 곳을 같이 고쳐야 하고, 실제로 product에만 들어간 채 남았다. 주석이 반대 사실을 적고 있어 다음 사람이 "신뢰 패키지를 더 추가"하는 식의 헛수고를 하게 된다
- **개선 방법**:
  1. `private <T> ConsumerFactory<String, T> consumerFactory(Class<T> type, String groupSuffix, String offsetReset, Map<String, Object> extra)` 하나로 4개를 만든다. `ErrorHandlingDeserializer` 감싸기(A-2)는 이 메서드 안에 둔다. product의 `setFailedDeserializationFunction(record -> null)`은 유지(리스너가 null을 처리하도록 이미 짜여 있음 — `ProductEventConsumer.java:26-30`)
  2. `addTrustedPackages(...)` 4곳과 `:66-67` 주석 삭제. `setRemoveTypeHeaders(true)`·`setUseTypeHeaders(false)`는 유지(발행 모듈의 클래스명 헤더를 무시하는 실제 동작)
  3. `application-prod.yml:31-33` 삭제(`group-id`·`bootstrap-servers`는 유지). `producer` 블록(`:34-36`)은 review 소유 → 판단 항목 3
  4. `KafkaProducerConfig.java:18`의 `@EnableKafka` 삭제(파일은 review 소유이지만 어노테이션 중복 제거는 동작 변화가 없다 — 그래도 사용자 확인 후)
  5. `ProductEventConsumer.java:20`의 `groupId`와 팩토리 값 중 하나를 없앤다 — 어느 쪽이 의도인지(`search-service` vs `ai-service`) 판단 항목 3
  - **review 팩토리(`reviewEventConsumerFactory`·`reviewEventListenerContainerFactory`)도 같은 메서드를 타게 되므로 구현 전 사용자 확인** — 빈 이름·타입·그룹·offset은 그대로 두어 review 컨슈머 동작은 변하지 않는다
- **검증 방법**: `./gradlew :baro-ai:compileJava` + 기동 후 `/actuator/beans`에서 4개 `*ListenerContainerFactory` 빈이 그대로 있는지. 로컬 Kafka에 `cart-events`·`product-events` 정상 이벤트 1건씩 넣어 각 컨슈머 로그가 찍히는지(그룹·offset 정책이 유지됐다는 확인). A-2 검증 절차를 같이 돌린다
- **API 영향**: No Impact
- **예상 변경 파일**: `config/KafkaConsumerConfig.java`, `config/KafkaProducerConfig.java`(어노테이션 1줄, 사용자 확인 후), `src/main/resources/application-prod.yml`, `search/infrastructure/event/ProductEventConsumer.java`(groupId 1줄, 판단 항목 3 결정 후)
- **예상 변경 라인 수**: Java 171 → 약 90(−80), yml −3
- **위험도**: Medium — 컨슈머 4개의 설정을 한 메서드로 모으므로 값 하나를 잘못 옮기면 그룹·offset이 바뀐다. 검증 절차로 잡는다
- **테스트 영향도**: 없음
- **예상 효과**: 코드 −80줄 · A-2 같은 공통 방어를 한 곳에서 적용 · 주석-동작 일치

### B-7. 안 쓰는 의존성·죽은 강제 버전 — `feign-okhttp`, `starter-validation`, swagger `force` 6줄 (H10)

- **Priority**: Low
- **Category**: Dead Code (의존성)
- **문제**: 세 묶음이 classpath에 있지만 어디에도 효과가 없다
- **근거**:
  - **`feign-okhttp:13.6`**(`build.gradle:88`) — `spring-cloud-openfeign-core-4.3.0.jar`의 `FeignAutoConfiguration$OkHttpFeignConfiguration`과 `loadbalancer.OkHttpFeignLoadBalancerConfiguration`은 둘 다 `@ConditionalOnProperty("spring.cloud.openfeign.okhttp.enabled")`(`matchIfMissing` 없음 → 명시적으로 `true`여야 활성)다. `grep -rn okhttp baro-*/src/main/resources` **0건**. 그래서 실제 HTTP 클라이언트는 `DefaultFeignLoadBalancerConfiguration`의 `feign.Client$Default`(JDK `HttpURLConnection`)다. `feign-hc5`도 없다(의존성 보고서 0건). **cart-recipe/001 C-4의 "지우면 안 된다"는 틀렸다** — okhttp jar가 있어도 켜지 않으면 쓰이지 않는다. 도입 커밋 `3d428e21`(#120 Cart Feign 설정)에 활성 속성이 같이 들어오지 않았다
  - **`spring-boot-starter-validation`**(`build.gradle:63`) — `grep -rn '@Valid\b\|@Validated\|jakarta.validation' baro-ai/src/main/java` **0건**(review 포함). `baro-common`에도 `jakarta.validation` 사용 0건. hibernate-validator는 이 스타터 하위로만 들어온다(의존성 보고서 508-511줄). keyword-search/001 A-4는 검증을 `ErrorCode` 방식으로 제안하므로 앞으로도 쓸 계획이 없다
  - **swagger `force` 6줄**(`build.gradle:38-43`) — 비-jakarta 3종(`swagger-core:2.2.22` 등)은 classpath에 아예 없다(의존성 보고서에서 `swagger-core:`·`swagger-models:`·`swagger-annotations:` 0건) → 강제할 대상이 없는 죽은 줄. jakarta 3종은 springdoc 2.8.5가 그대로 2.2.28을 가져오므로(29-33줄, 화살표 없음) 강제가 없어도 같은 버전이다. 도입 커밋 `52b57a63`·`70b0c028`(#103/#105, Spring AI 1.0.0-M5 초기화) — M5는 [`decisions/003`](../../decisions/003-gemini-migration.md)에서 1.1.8로 올라가 전제가 사라졌다
  - 유지할 것: `springdoc`(`SwaggerConfig`·`@Tag/@Operation` 사용), `caffeine`(`CacheConfig`), `spring-kafka`, `actuator`(A-1), `spring-dotenv`는 판단 항목 5
- **왜 문제인가**: 각 줄이 "왜 있는지"를 설명하지 못하고, `feign-okhttp`는 있는 줄 알고 타임아웃 튜닝을 OkHttp 속성으로 시도하면 아무 일도 안 일어난다(`cart-recipe/001` B-5의 타임아웃은 `spring.cloud.openfeign.client.config.*`라 영향 없음)
- **개선 방법**: `build.gradle:63`·`:88`·`:36-46` 중 swagger 6줄 삭제(`elasticsearch-java` force는 B-8). `configurations.all { resolutionStrategy { … } }` 블록은 B-8까지 반영하면 통째로 없어진다. OkHttp를 실제로 쓰고 싶다면 삭제 대신 `spring.cloud.openfeign.okhttp.enabled: true`를 넣어야 하는데, 그 근거(`Client.Default`가 부족한 실측)가 없으므로 삭제를 권한다
- **검증 방법**: `./gradlew :baro-ai:compileJava` + 의존성 보고서에서 `feign-okhttp`·`okhttp3`·`hibernate-validator` 0건. 기동 후 추천 API 1회로 Feign 호출이 전과 같이 나가는지(클라이언트가 원래 `Client.Default`였으므로 동작 변화 없음)
- **API 영향**: No Impact
- **예상 변경 파일**: `build.gradle`
- **예상 변경 라인 수**: −8
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 의존성 3종(okhttp·hibernate-validator·tomcat-embed-el) 제거 · 빌드 파일에서 이유 없는 줄 제거

### B-8. Elasticsearch 클라이언트·Spring Data 버전 고정 해제 — 전제가 사라졌고 조합이 어긋나 있다 (vector-similarity/001 C-2 → 이관)

- **Priority**: Medium
- **Category**: Spring (의존성 정합)
- **문제**: `elasticsearch-java`를 8.13.3으로, `spring-data-elasticsearch`를 5.3.6으로, `spring-data-commons`를 3.3.6으로 강제한다. 고정 이유였던 Spring AI 1.0.0-M5 호환은 [`decisions/003`](../../decisions/003-gemini-migration.md)으로 사라졌고, 그 결과 한 classpath 안에서 버전이 세 갈래로 어긋난다. ES 헬스 인디케이터도 이 호환 문제를 이유로 꺼져 있다
- **근거**: `vector-similarity/001` C-2가 확인한 사실을 그대로 인정한다 — `build.gradle:31-32,44,69-72`, 의존성 보고서 593줄 `spring-data-elasticsearch:5.5.6 -> 5.3.6`(강등), 596-597줄 `spring-data-commons:3.5.6 -> 3.3.6`, 611-612줄 `elasticsearch-java:8.13.3` vs `elasticsearch-rest-client:8.13.4 -> 8.18.8`(API 클라이언트와 저수준 클라이언트 버전이 다름). 커밋 `e0b3429e`·`5d5728b6`의 고정 사유가 "Spring AI M5 호환". `application-prod.yml:97-99` 헬스 인디케이터 off 사유도 "Java client 8.13.3 호환". 이 문서에서 추가로 확인한 것: `spring-data-jpa`는 baro-common 경유로 **3.5.6**(의존성 보고서 146줄)인데 `spring-data-commons`는 3.3.6으로 눌려 있다 — JPA 자동설정을 제외해서(B-1) 드러나지 않을 뿐 정상 조합이 아니다
- **왜 문제인가**: Boot가 검증한 조합(5.5.6 / 3.5.6 / 8.18.8)을 버리고 아무도 검증하지 않은 조합을 쓰고 있다. `vector-similarity/001` C-1의 native `knn` 전환은 새 클라이언트 API가 필요하다
- **개선 방법**: `build.gradle:30-33`(`dependencyManagement.dependencies` 2줄)·`:44`·`:69-72`의 exclude/명시 버전을 지우고 `implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'` 한 줄로 되돌린다. 컴파일이 통과하면 `application-prod.yml:97-99` 헬스 인디케이터를 다시 켠다(라이브니스 프로브가 `/actuator/health`라 ES 장애가 Pod 재시작으로 이어지는지는 판단 항목 8). **STOP 게이트**: review 코드도 같은 클라이언트 API(`ElasticsearchClient`·집계 타입 — `review/infrastructure/bestreview/*` import 확인)를 쓰므로, `compileJava`에서 review 패키지에 오류가 나면 거기서 멈추고 사용자에게 확인한다(review 코드 변경은 이 라운드 범위 밖)
- **검증 방법**: (1) `./gradlew :baro-ai:compileJava` — search·recommend·embedding·log·review 전부 통과. (2) 의존성 보고서에서 `elasticsearch-java`·`elasticsearch-rest-client`가 같은 8.18.x, `spring-data-elasticsearch` 5.5.6, `spring-data-commons` 3.5.6. (3) 로컬 ES(8.12)로 기동해 검색·색인·유사 상품 API 1회씩 + `/actuator/health`의 `elasticsearch` 항목 `UP`
- **API 영향**: No Impact
- **예상 변경 파일**: `build.gradle`, `src/main/resources/application-prod.yml`
- **예상 변경 라인 수**: −8, −3
- **위험도**: Medium — 클라이언트 마이너 5단계 상승. 컴파일과 위 3개 API 호출로 잡히지 않는 런타임 차이는 로컬 ES 8.12(클라이언트가 서버보다 새로움)에서 확인한다. 운영 ES 9.1.2와의 관계는 판단 항목 8
- **테스트 영향도**: 없음
- **예상 효과**: Boot 관리 버전으로 통일 · 헬스 체크 복구 · C-1(knn) 전환의 전제 확보

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 설정 클래스가 `common.config`(1개)와 `config`(8개) 두 패키지에 나뉘어 있음 (H8)

`SwaggerConfig`만 `common/config/`, 나머지 8개는 루트 `config/`다. `spring-boot-java-design.md` Style 절은 "공통 설정: `common/config/`, 도메인 전용: `{domain}/config/`"라 하고, 루트 `config/`에는 review 전용 2개(`NegativeReviewPropertiesConfig`·`SentimentPropertiesConfig`)와 `KafkaProducerConfig`(review만 사용)가 공유 설정과 섞여 있다. **왜 지금 안 하나**: 순수 이동이라 동작 변화는 없지만 8개 파일의 패키지 선언과 `docs/specs/README.md`의 클래스 경로 표가 같이 바뀌고, 그중 3개는 review 소유라 팀원 합의가 먼저다. B-6로 `KafkaConsumerConfig`를 크게 고칠 때 그 파일만 옮기는 것도 어색하므로 별도 라운드로 미룬다.

### C-2. `@Operation(description=…)`·`@Parameter(example=…)` 사용이 규칙과 다르지만, 규칙 쪽이 baro-ai 실물과 안 맞음 (H7 후반)

`openapi-conventions-operation.md`는 "`description` 속성을 쓰지 않고 Javadoc을 `therapi-runtime-javadoc`이 반영한다", "`bearer-jwt` 전역 security"를 전제하는데, baro-ai classpath에 therapi가 없고(의존성 보고서 0건) `SwaggerConfig`에 security 스키마도 없다. 컨트롤러가 `description`을 쓰는 건(`SearchController.java:33,49`, `RecommendationController.java:33-36` 등) 이 환경에서는 오히려 맞는 방법이다. **왜 지금 안 하나**: 고칠 대상이 코드가 아니라 TripFit에서 물려받은 규칙 파일이다 — `retro` 스킬로 `harness-retro.md` 후보에 올리는 게 맞다(코드·설계 개선이 아니라 하네스 결함, `core-followup.md` 경계).

### C-3. `baro-common`의 `HistoryLogAutoConfiguration`이 baro-ai에서 활성화돼 쓰이지 않는 빈 2개를 만듦

`baro-common/.../AutoConfiguration.imports`로 등록된 이 자동설정은 `@ConditionalOnClass(KafkaTemplate)`·`@ConditionalOnBean(KafkaTemplate)`이라 baro-ai의 `reviewSummaryKafkaTemplate`을 잡아 `HistoryLogWriter`·`HistoryAspect`를 만든다. baro-ai에는 `@TrackHistory`가 없어(취향 벡터 파이프라인에서 baro-ai는 **소비자**다) 둘 다 호출되지 않는다. `history.log.enabled: false` 한 줄로 끌 수 있다. **왜 지금 안 하나**: 빈 2개가 노는 것 외 비용이 없고, `HistoryLogWriter`가 review의 `KafkaTemplate<String, ReviewSummaryEvent>`를 `KafkaTemplate<?, ?>`로 잡는 구조는 baro-common 설계라 baro-ai에서 끄는 게 맞는지 그쪽 소유자 확인이 먼저다.

### C-4. `docs/README.md`의 패키지 트리가 현재 구조와 다름

`docs/README.md:183-192`는 `web/`·`persistence/jpa/`·`persistence/elasticsearch/`·`messaging/` 구조를 그리는데, 실제 패키지는 `{domain}/{presentation|application|domain|infrastructure}`(`AGENTS.md`)이고 JPA 디렉터리는 존재한 적이 없다. 같은 문서 `:296`은 "RDB·JPA 엔티티가 없다"고 맞게 적고 있어 문서 안에서도 어긋난다. **왜 지금 안 하나**: `{{아키텍처 개요}}` 문서의 절 하나를 다시 그리는 문서 작업이라 B-1 반영 커밋에 끼우면 리뷰 범위가 흐려진다. B-1 반영 후 별도 `[Docs]` 커밋으로 제안한다.

### C-5. `AsyncConfig`의 `executor.initialize()` 호출은 불필요

`AsyncConfig.java:29` — `ThreadPoolTaskExecutor`는 `InitializingBean`이라 빈으로 등록되면 Spring이 `afterPropertiesSet()`에서 같은 초기화를 한다. 두 번 불려도 무해하다. **왜 지금 안 하나**: 1줄이고 동작 차이가 없다. H11 결론(`AsyncConfig`·`CacheConfig`·`LlmClientConfig`는 죽은 설정 없음)에 덧붙이는 관찰이다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 컨슈머 팩토리를 없애고 yml `spring.kafka.consumer.*` + Boot 기본 팩토리 하나로 되돌리지 않는다

B-6의 반대 방향이다. 컨슈머 4개는 그룹(`ai-service`·`-cart`·`-order`·`-review`)과 offset 초기값(product `latest`, 나머지 `earliest`), product의 `max.poll.records=1`·재시도 정책(`search/001` A-1)이 실제로 다르다. yml 하나로는 이 차이를 표현할 수 없고, `@KafkaListener(properties=…)`로 흩뿌리면 지금보다 읽기 어렵다. 공통 메서드 + 차이 나는 인자 4개가 이 규모에 맞는 형태다.

### D-2. 응답 규격을 `ResponseDto`를 버리고 날 반환으로 통일하지 않는다

B-5의 반대 방향이다. 에러 응답은 `baro-common` `BaseExceptionHandler`가 이미 `ResponseDto`로 내보내고, 그 핸들러는 baro-ai가 소유하지 않는다. 날 반환으로 통일하려면 예외 핸들러를 baro-ai에서 새로 짜야 하고 baro-shopping 등 Feign 상대와도 어긋난다. 감싸는 쪽이 변경 범위가 작고 저장소 규약과 맞다.

### D-3. `spring-boot-starter-actuator`를 baro-ai `build.gradle`에서 지우지 않는다

A-1 근거대로 actuator는 baro-common 경유로도 들어온다(의존성 보고서 424·622줄). 그래서 baro-ai의 명시 선언(`build.gradle:80`)은 기술적으로 중복이지만, 001 B-1이 "왜 이 모듈이 지표를 켜는지"를 그 줄의 주석으로 남겨 두었다. baro-common이 나중에 actuator를 빼도 baro-ai 지표가 사라지지 않게 하는 의도적 선언으로 본다.

### D-4. `autoconfigure.exclude`의 DataSource·Hibernate 제외 2줄을 지우지 않는다

B-1에서 설명했다 — baro-common이 JPA 스타터를 `implementation`으로 노출하는 한 이 제외가 없으면 기동이 실패한다. 근본 해결은 baro-common이 JPA를 `compileOnly`로 바꾸거나 별도 모듈로 나누는 것인데 저장소 루트 영역이라 이 하네스 범위 밖이다(판단 항목 9).

## 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있을 때만)

- **Monitoring (Prometheus 노출)** — **Now**. A-1과 동일. 근거는 A-1에 있다. 장점: yml 1줄로 이미 배포된 수집 파이프라인이 살아난다. 단점: 없음(트레이싱은 이미 켜져 있어 추가 의존성도 없다)
- **Resilience (컨슈머 poison-pill 방어)** — **Now**. A-2와 동일
- **Resilience (Feign HTTP 클라이언트 교체 — OkHttp/HC5 활성)** — **Later**. B-7에서 실제 클라이언트가 JDK 기본(`Client.Default`)임을 확인했다. 커넥션 풀이 없는 클라이언트라 호출량이 늘면 바꿀 이유가 생기지만, 지금은 요청당 Feign 호출 1회이고 초당 요청 수 추정치가 없다(`[미정]`, 001 판단 항목 5). 바꿀 때는 jar만 두는 게 아니라 `spring.cloud.openfeign.{okhttp|httpclient.hc5}.enabled: true`를 같이 넣어야 한다는 게 이번 라운드의 교훈이다

## 판단이 서지 않는 항목

1. **`chat` 스텁 4개 삭제 여부 (H3)** — `ChatbotController`(엔드포인트 0개)·`ChatService`(로직 0줄)·`ChatRequest`·`ChatResponse`(어디서도 참조 안 됨). 스펙 [`chatbot.md`](../../specs/chat/chatbot.md)는 "목적 미정", `mvp.md:24`는 "미착수(TBD)". 감사자 권고는 **삭제**다 — 백로그 기록은 스펙·mvp 표가 이미 하고 있고, `@RestController`가 빈 채로 떠서 `/api/v1/chatbot` 프리픽스만 Swagger에 잡힌다. 다만 "자리를 남겨 두려는" 의도일 수 있어 사용자 결정으로 남긴다. 삭제 시 변경: 파일 4개(−45줄), `docs/specs/README.md` chat 행의 "`chat.*` 스텁" 문구, `chatbot.md` 배경 절의 코드 링크 2개
2. **`org.springframework.ai: DEBUG` (H6)** — B-4에서 뺐다. 001 반영 검증 절차 2번("DEBUG 로그의 요청 본문에 `responseMimeType`·`responseSchema`가 있는지")이 아직 실행되지 않았고, 이 로그가 LLM 요청 본문을 보는 유일한 수단이다. 검증이 끝난 뒤 INFO로 내릴지, 남길지 사용자가 정한다
3. **`spring.kafka.producer.*` yml(`application-prod.yml:34-36`)·`KafkaProducerConfig`·`ProductEventConsumer`의 `groupId`** — producer yml은 Boot의 `kafkaProducerFactory`가 `@ConditionalOnMissingBean(ProducerFactory)`로 물러나 있어 죽은 키다(B-6 근거와 같은 jar). 그러나 `KafkaProducerConfig`·`ReviewSummaryEventProducer`는 review 소유라 yml 삭제도 팀원에게 알린 뒤 한다. `ProductEventConsumer.java:20`의 `groupId = "search-service"`와 팩토리의 `ai-service` 중 어느 그룹이 의도인지도 사용자 확인 — 그룹을 바꾸면 offset이 처음부터(`latest`) 다시 잡힌다
4. **review 컨슈머 팩토리에 A-2 방어를 같이 넣을지** — `reviewEventConsumerFactory`(`:149-162`)도 같은 poison-pill 위험이 있고 B-6의 공통 메서드를 타면 자동으로 감싸진다. review 코드 변경은 아니지만 review 컨슈머의 실패 동작이 "무한 재시도 → 로그 후 건너뜀"으로 바뀌므로 팀원 확인 대상
5. **`spring-dotenv:4.0.0`(`build.gradle:54`) 유지 여부** — 작업 디렉터리의 `.env`를 환경변수처럼 읽는 라이브러리다. `baro-ai/.env`·`.env.example`이 없고(`.gitignore:85`가 `.env`를 무시), `IMPLEMENTATION.md:89-92`는 `export`로 안내하며, 다른 모듈은 이 라이브러리를 쓰지 않는다(`grep dotenv */build.gradle` baro-ai만). 로컬에서 개인적으로 `.env`를 쓰고 있다면 유지, 아니면 삭제(1줄). 사용자만 안다
6. **기본 프로필 `local`·`application-local.yml` 부재** — `application.yml:10-11`은 기본 프로필을 `local`로 두지만 그 파일이 없고, 기본 프로필로 뜨면 `KafkaConsumerConfig.java:34`의 `@Value("${spring.kafka.bootstrap-servers}")`(기본값 없음)에서 placeholder 오류로 기동이 실패한다. `IMPLEMENTATION.md:89`가 prod를 export하라고 하는 이유다. `cart-recipe/001` 판단 항목 2·C-6(`recipe-recommend.yml` import가 prod에만 있음)과 같은 결정이다. 선택지: (a) `application-prod.yml` 내용을 `application.yml`로 올리고 프로필 개념을 없앤다(환경 차이는 이미 전부 환경변수) (b) `application-local.yml`을 만든다. 감사자 권고는 (a) — 다른 모듈은 local/prod 두 파일을 쓰지만 baro-ai는 프로필로 갈리는 값이 없다. 사용자 결정
7. **`ReviewBatchController`의 `Map<String,String>` 반환(`:26-29,33-36`)** — B-5와 같은 envelope 불일치지만 review 소유. 사용자가 팀원에게 전달할지 결정
8. **ES 서버 버전과 헬스 인디케이터 재활성화의 운영 영향** — 로컬 `docker-compose.data.yml` 8.12.0, 운영 `bitnami/elasticsearch:9.1.2`(`vector-similarity/001` C-2). B-8로 클라이언트를 8.18.8로 올려도 9.x 서버와의 호환은 공식 문서에서 확인하지 못했다. 또 헬스 인디케이터를 켜면 `/actuator/health`가 ES 상태를 포함하는데, `deployment.yml:53-66`의 라이브니스·레디니스 프로브가 같은 경로라 **ES 장애 시 Pod가 재시작**된다 — 그게 의도인지 배포 담당자 확인(`management.endpoint.health.probes`·`group` 분리로 피할 수 있다). 둘 다 저장소 루트·k8s 영역이라 여기서 결정하지 않는다
9. **`baro-common`이 JPA·AOP·S3·Kafka·actuator를 전부 `implementation`으로 노출하는 구조** — baro-ai가 JPA를 지워도 Hibernate가 남는 원인(B-1)이고, `HistoryLogAutoConfiguration`이 baro-ai에서 활성화되는 원인(C-3)이다. 저장소 루트 모듈이라 STOP §0 — 관찰만 남기고 사용자가 팀에 제안할지 정한다
10. **k8s `SPRING_CLOUD_CONFIG_ENABLED`·`SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED`(`deployment.yml:33-36`)** — B-2 반영 후에는 아무 효과가 없는 환경변수가 된다. 지워도 무해하지만 `k8s/`는 쓰기 범위 밖 — 배포 담당자에게 전달

## 승인 결과 (2026-09-05)

사용자가 A·B 전 항목을 승인했다. 판단 항목 답: **1번 `chat` 스텁 4개 삭제**(스펙 `chatbot.md`는 백로그 기록으로 유지) · **4번 review 팩토리는 건드리지 않는다** — A-2 방어는 cart·order 팩토리에만 넣고, B-6 공통 메서드로 review까지 합치는 것은 review 팀원 동의 후로 미룬다 · 8번 ES 버전은 인프라 담당자 확인 요청(팀 전달) · 7·9·10번은 팀 전달. 나머지는 구현 세션에서 결정한다.

원래 승인 대기 문구: 사용자 승인 후 A/B 항목만 우선순위 순으로 구현한다. 제안 순서: A-1 → A-2(B-6과 한 변경) → B-3 → B-4 → B-1 → B-7 → B-5 → B-2(cart-recipe/001 A-2 반영 확인 후) → B-8(review 컴파일 STOP 게이트). C/D는 이번 라운드에서 수정하지 않는다. 판단 항목 1·3·4·5·6은 구현 전에 답이 필요하다.

### 구현 결과 (2026-09-05)

A 2건·B 8건 중 **B-8을 뺀 9건**을 반영했다. 무엇을 바꿨고 무엇을 남겼는지, 검증 결과는 [`refactor-log.md`](refactor-log.md) 2026-09-05 두 번째 항목에 있다.

| 번호 | 결정 | 반영 위치 |
|------|------|-----------|
| 1 | **`chat` 스텁 4개 삭제**(승인 결과에서 이미 결정). 스펙 `chatbot.md`는 백로그 기록으로 유지하고 "코드 없음"으로 갱신 | `chat` 패키지 삭제 · `specs/README.md` |
| 2 | **`org.springframework.ai: DEBUG`를 끈다.** 대신 `loggers` actuator 엔드포인트를 열어 필요할 때 재배포 없이 켰다 끈다 — 상시 로깅은 장바구니 재료가 계속 남는 반면, 확인은 어쩌다 한 번이기 때문(사용자 결정) | `application.yml` 로깅·management 절 |
| 3 | **컨슈머 그룹은 `ai-service` 계열로 통일**(사용자 결정 — dev 모드라 offset 초기화 부담 없음). `ProductEventConsumer`의 `groupId = "search-service"` 어노테이션 값을 지워 팩토리 값만 남겼다. `spring.kafka.consumer`의 죽은 키 3개는 삭제하고 `producer` 블록은 review 소유라 두었다 | `ProductEventConsumer` · `application.yml` |
| 4 | **review 팩토리는 건드리지 않는다**(승인 결과에서 이미 결정). 공통 메서드는 product·cart·order 3개만 쓴다 | `KafkaConsumerConfig` |
| 5 | **`spring-dotenv` 삭제** — Gemini를 아직 연결하지 않아 `.env`를 쓰고 있지 않다(사용자 확인). 필요해지면 한 줄로 되돌린다 | `build.gradle` |
| 6 | **프로필을 없앤다**(a안, 감사자 권고 = 사용자 결정). `application-prod.yml`을 `application.yml`로 합쳐 파일 하나로 만들었다. 프로필 지정 없이 기동되고, `recipe-recommend.yml` import가 항상 로드된다([`cart-recipe/001`](../cart-recipe/001-cart-integration-and-request-path.md) C-6도 함께 해소) | `application.yml` · `IMPLEMENTATION.md` |
| 7·9·10 | **팀 전달**(승인 결과에서 이미 결정) | — |
| 8 | **B-8 보류** — 아래 참고 | — |

**B-8은 반영하지 않았다.** 버전 고정을 풀자 `elasticsearch-java`가 8.18.x로, `spring-data-elasticsearch`가 5.5.6으로 올라가면서 baro-ai 코드 3곳이 컴파일에 실패했다(`FetchSourceFilter.of`·`RangeQuery.Builder.field`·`Script.Builder.inline` 시그니처 변경). 마이그레이션 자체는 3곳이라 작지만, **[`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) 승인 결과가 "ES 서버 9.1.2 ↔ 클라이언트 호환은 인프라 담당자 답변 전까지 버전 고정을 바꾸지 않는다"로 정해 둔 항목**이라 되돌렸다. 답이 오면 이 3곳 마이그레이션과 함께 다시 시도한다. 고정 이유를 `build.gradle` 주석에 남겨 다음 사람이 근거를 찾을 수 있게 했다.

**B-6에서 감사안과 다르게 간 곳**: 감사는 product 팩토리의 `setFailedDeserializationFunction(record -> null)`을 유지하라고 했으나 삭제했다. 셋 다 같은 실패 동작(에러 핸들러가 로그 후 건너뜀)을 갖는 편이 낫고, 그래야 "product만 다르다"는 이 항목의 문제의식과도 맞기 때문이다. 도달할 수 없게 된 `ProductEventConsumer`의 `event == null` 분기도 같은 변경에서 정리했다.
