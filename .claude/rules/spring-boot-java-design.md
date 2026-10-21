---
paths:
  - "**/*.java"
---

# Spring Boot / Java — 설계 원칙 · 스타일 · 테스트

패키지 구조·레이어·문서 모델·Enum·표기규칙·네이밍 원칙은 [`spring-boot-java.md`](spring-boot-java.md) — 여기는 SOLID/OOP 원칙·외부 I/O 경계·Style·API 추가 시 규칙·Configuration·Tests를 다룬다.

> **baro-ai에는 RDB·JPA가 없다.** 저장소는 Elasticsearch뿐이고 `@Transactional`을 쓰는 코드가 한 줄도 없다(2026-09-08 확인). 아래 "외부 I/O 경계" 절은 그 전제 위에 있다.
>
> 트랜잭션이 없다는 것은 **여러 문서를 함께 바꾸는 일을 원자적으로 만들 수단이 없다**는 뜻이다. 그런 일이 생기면 보호 장치를 얹기 전에 **그 일 자체를 없앨 수 있는지** 먼저 본다 — 상품명을 두 인덱스에 나눠 쓰던 구조를 한 인덱스의 서브필드로 합쳐 이중 쓰기를 없앤 것이 그 사례다(2026-09-08).

## SOLID / OOP 원칙 (실용적 적용)

**전제 — 이 저장소는 풀 DDD·포트/어댑터 인터페이스 레이어를 채택하지 않는다.** 구현체가 하나뿐인 의존성에 인터페이스를 미리 두지 않는다. 아래 규칙은 그 범위에서만 SOLID/OOP를 적용한다 — 추상화를 위한 추상화(불필요한 인터페이스·레이어 추가)는 여기서도 금지다(`baro-ai-performance-refactor.md` "오버엔지니어링 금지").

### SRP — Service 책임 분리

- `*Service` 클래스는 **하나의 응집된 유스케이스 그룹**만 담당한다. 서로 다른 책임(예: 멤버십 관리 + 추천 로직)이 한 클래스에 섞이면 분리 신호다.
- 이미 확립된 분리 패턴: 색인(`ProductIndexService`)과 조회(`ProductSearchService`)를 나눠 둔 것 — 이름이 하는 일과 맞아야 읽는 사람이 헤매지 않는다(자동완성 **조회**가 색인 서비스에 들어 있던 것을 옮긴 이력: `audits/keyword-search/001` B-1). Service가 커지면 같은 축(쓰기/읽기)으로 나눈다.
- 참고용 정량 신호(기계적 강제 아님): 한 Service가 300~400줄을 넘거나 서로 다른 도메인 개념을 다루면 분리를 검토한다. 줄 수만으로 쪼개지 않는다 — Controller처럼 실제로는 얇은 위임 + Swagger 어노테이션이 대부분이면 분리 대상이 아니다.

### OCP / ISP — 인터페이스는 다형성이 실제로 필요할 때만

- 기본은 **concrete 클래스 직접 주입**(`@RequiredArgsConstructor` + `private final`)이다. 구현체가 하나뿐인 의존성에 "나중에 갈아끼울 수도 있으니" 인터페이스를 미리 만들지 않는다 — 위 포트 폐기 사유와 동일한 이유다.
- 인터페이스가 정당화되는 경우: **실제로 다형성이 있는** 의존성만. 이 저장소에서는 `ElasticsearchRepository`(Spring Data가 구현체를 생성)와 `BaseErrorCode`(도메인마다 다른 enum이 구현) 정도다.
- 새 인터페이스를 추가하기 전 "지금 구현체가 2개 이상 존재하거나, 곧 그렇게 될 근거가 있는가?"를 먼저 답한다 — 아니면 concrete 클래스로 시작한다.

### 캡슐화 — 문서 모델은 의도가 드러나는 메서드로 상태를 바꾼다

- `@Document` 클래스에 **클래스 레벨 `@Setter` 금지**(아래 **Style** 절). 여러 필드를 함께 바꾸는 상태 전이는 Service에서 setter를 여러 번 호출하지 않고, 문서 클래스에 **의도가 드러나는 메서드**를 두고 그 안에서 처리한다. 무결성 조건이 있으면 그 검증도 메서드 안에 둔다.
- 색인 문서는 대부분 **만들어서 통째로 저장**하는 모델이라 `@Builder` 팩토리로 생성하고 이후 변경하지 않는 편이 자연스럽다 — 재처리가 덮어쓰기가 되도록 결정적 `@Id`를 쓰는 것과 같은 맥락이다.
- **Law of Demeter — 과도한 getter 체이닝 자제.** 문서 모델은 평평해서 체이닝이 깊어질 일이 드물지만, **3단계 이상** 이어지거나(`a.getB().getC().getD()`) 같은 체이닝을 여러 Service 메서드에서 반복해야 한다면 중간 객체에 조회 메서드를 두거나 필요한 값만 직접 가져오는 방식으로 승격한다.
- 이 규칙은 풀 DDD(애그리거트 불변식·행위 중심 모델)를 의미하지 않는다 — 여기서 막는 건 "여러 필드를 흩어진 setter 호출로 바꾸는" 절차적 패턴과 과도한 getter 체이닝 두 가지뿐이다.

## 외부 I/O 경계

baro-ai에는 RDB 트랜잭션이 없다(`@Transactional` 사용처 0건). 대신 이 서비스가 실제로 다루는 경계는 **외부 호출**이다 — Elasticsearch, Gemini, `baro-shopping` Feign, Kafka.

### 상한 없는 호출을 만들지 않는다

블로킹 호출의 타임아웃은 곧 **스레드를 붙잡는 시간**이고, 그게 이 서버가 동시에 받을 수 있는 요청 수를 정한다. 새 외부 호출을 추가하면 연결·읽기 상한을 함께 정한다.

| 대상 | 현재 상한 | 어디에 |
|---|---|---|
| Elasticsearch | 연결 1초 · 응답 3초 | `application.yml` `spring.elasticsearch.*` (2026-09-09에 응답 상한을 30초에서 낮췄다 — 30초는 RestClient 기본값을 그대로 적어 둔 값이었고 실제 조회는 p95 8ms 미만이다) |
| `baro-shopping` Feign | 연결 1초 · 읽기 3초 | `spring.cloud.openfeign.client.config.shopping-service` |
| Gemini (요청 경로) | 호출당 3초 · 재시도 없음 · 동시 5건 | `LlmClientConfig`의 `interactiveGenAiClient` + `LlmGateway` 격벽 |
| Gemini (배치 경로) | 호출당 30초 · 지수 백오프 3회 | `LlmClientConfig`의 `googleGenAiClient` (SDK에 직접 건다 — `spring.ai.retry.*`는 이 SDK 예외에 반응하지 않는 죽은 설정) |

**LLM 호출은 `LlmGateway` 하나를 거친다.** 경로에 맞는 예산을 고르고, 요청 경로의 동시 호출을 5건으로 묶고, 응답 스키마를 강제하고, 지연·실패를 `llm.call` 지표에 남기는 일이 거기 모여 있다. 새 LLM 호출을 추가할 때 `ChatClient`를 직접 주입받지 않는다 — 그러면 예산과 지표가 다시 갈라진다.

**상한은 개별 값이 아니라 예산이다.** 게이트웨이 응답 상한(5초) 안에서 안쪽 직렬 합이 얼마인지 따져 본다 — 안쪽 합이 바깥 상한을 넘으면 사용자는 이미 끊긴 뒤인데 서버 스레드만 계속 붙잡혀 있다.

### 요청 경로에서 부가 작업을 분리할 때

부가 데이터 쓰기(로그 적재 등)를 비동기로 넘기는 것은 **문제를 없애는 게 아니라 형태를 바꾸는 것**이다 — 응답 지연이 유실로 바뀐다. 넘기기 전에 "그 작업이 유실돼도 되는가"에 먼저 답한다. 실제로 검색 로그를 비동기로 넘겼다가 **37~65%가 조용히 버려지는 것**을 실측으로 발견했고, 결국 저장 방식을 고치는 대신 그 신호 자체를 없앴다(`audits/taste-vector/003`·`005`).

- 비동기 실행자에 넘길 때는 큐 초과 시 동작(폐기 / 예외)을 명시적으로 정한다. 기본 `AbortPolicy`는 예외가 호출자(Kafka 컨슈머)로 올라가 재시도 폭풍을 만든다.
- `@Async`는 **다른 빈을 거쳐야** 프록시를 탄다. 같은 클래스 안에서 자기 호출하면 어노테이션이 있어도 동기로 돈다 — 이 사고가 실제로 있었다(`audits/taste-vector/001` A-2). **2026-09-08 기준 이 모듈에 `@Async`를 쓰는 코드는 없다** — 유일한 사용처였던 취향 벡터 갱신을 읽기 시점 계산으로 바꾸면서 `AsyncConfig`와 함께 지웠다.
- **미리 계산해 저장할지, 읽을 때 계산할지**도 같은 축의 판단이다. 미리 만들어 두면 쓰기가 늘고(이벤트 1건당 왕복 4회), 동시 갱신에서 나중 쓰기가 먼저 쓰기를 덮어쓰며, 읽는 쪽은 항상 조금 옛 값을 본다. 계산이 가벼우면 읽을 때 만드는 편이 이 셋을 한 번에 없앤다 — 취향 벡터가 그 사례다.

### 재처리가 중복을 만들지 않게 한다

Kafka 재시도·재처리에서 같은 이벤트가 두 번 올 수 있다. 문서 ID를 **메시지 위치로 결정적으로** 만들면(`{파티션}-{오프셋}`) 재처리가 덮어쓰기가 된다. 자동 생성 ID에 맡기면 로그가 중복으로 쌓인다.

### 부분 실패를 격리한다

묶음 처리에서 하나가 실패했을 때 나머지를 잃지 않게 한다. spring-kafka 배치 리스너는 일반 예외를 던지면 묶음 전체를 재시도한 뒤 **전부 폐기**하므로, 실패 위치를 담은 `BatchListenerFailedException`을 던져 그 앞은 커밋하고 그 자리부터 재시도하게 한다(`audits/keyword-search/002`).

## Style

- Java 21 (records, pattern matching) 사용 가능
- Lombok — `@Document` 클래스·`@ConfigurationProperties` 클래스·**Service**·`*ErrorCode` enum만 사용. Controller/DTO(record)는 미사용.
  - 문서 모델: **클래스 레벨 `@Getter`만.** `@Setter`는 클래스 레벨에 두지 않는다 — 상태 전이는 도메인 메서드로 한다(위 **캡슐화** 절). 생성은 `@Builder`를 쓴다.
  - `*ErrorCode` enum: `@Getter` + `@AllArgsConstructor` (`BaseErrorCode` 구현)
  - Service: 생성자 주입 필드는 `@RequiredArgsConstructor` + `private final` — 수동 생성자 작성 금지(baro-ai에는 ArchitectureTest가 없어 필드 주입 금지·생성자 스타일 **둘 다** 리뷰로 확인한다)
    - **예외 — 수동 생성자 유지:** 생성자 바디에 필드 대입 외 로직(검증·파생값 계산)이 있거나, `@Lazy`처럼 **생성자 파라미터에만** 걸려야 의미가 살아나는 스프링 애너테이션이 있는 경우(필드에 붙이면 Lombok이 생성자로 복사하지 않는다). 이런 경우 수동 생성자를 유지하고 이유를 `//` 한 줄로 남긴다
- 설정 클래스는 최상위 `config/`에 모은다(`CacheConfig`·`KafkaConsumerConfig`·`KafkaDeadLetterConfig`·`LlmClientConfig` 등). 도메인 전용 설정만 `{domain}/config/`에 둔다.
- **아무도 읽지 않는 설정 빈을 만들지 않는다.** `ElasticsearchConfig`의 `ClientConfiguration` 빈이 실제로는 아무 효과가 없었는데 문서에는 그 값이 사실처럼 적혀 있었다(`audits/vector-similarity/001` A-2). 설정은 Spring이 원래 읽는 프로퍼티 경로에 두는 편이 안전하다.

### DTO record 가독성

- API DTO record 컴포넌트(필드)는 **필드마다 빈 줄로 구분**, `@Schema` / validation / 타입을 **각각 별도 줄**
- Eclipse Spotless는 record 컴포넌트를 **method parameter**와 동일 규칙으로 포맷한다 → 컨트롤러 한 줄 파라미터 설정과 충돌
- 따라서 다필드 DTO record는 `// @formatter:off` … `// @formatter:on`으로 가독성 레이아웃을 고정한다

### Controller 메서드 파라미터

- 파라미터 어노테이션은 **같은 줄**에 붙인다. 어노테이션만 단독 줄로 쪼개지 않음
- 기준: `config/{프로젝트}-java-format.xml` — `insert_new_line_after_annotation_on_parameter=do not insert`

```java
// ✅
ResponseDto<CustomPage<ProductSearchResponse>> search(
		@RequestParam(required = false) String q,
		@RequestParam(defaultValue = "0") int page) { ... }

// ❌ 어노테이션·타입·이름을 한 줄씩 분리
ResponseDto<...> search(
		@RequestParam(required = false)
		String q,
		@RequestParam(defaultValue = "0")
		int page) { ... }
```

## API (추가 시)

- `@RestController` + `@RequestMapping("/api/v1/...")`
- 응답 envelope: `baro-common`의 `ResponseDto`(목록은 `CustomPage`) — 이 저장소가 직접 정의하지 않는다
- 요청 검증: Jakarta Validation (`@Valid`)
- 예외: `@RestControllerAdvice`(`common/exception/GlobalExceptionHandler`) + `BaseErrorCode`
- 문서: springdoc — `@Tag`·`@Operation`·`@Schema` 작성 규칙은 `openapi-conventions.md`, enum 목록은 [`spring-boot-java.md`](spring-boot-java.md) **Enum** 절
- Controller 파라미터 스타일: 위 **Controller 메서드 파라미터** 절 준수
- **계약 변경(필드 추가·삭제·이름변경·타입변경·필수화, enum 값, `ErrorCode`, 경로·메서드):** baro-ai는 릴리즈 전이라 계약을 **바꿔도 된다**(`baro-ai-performance-refactor.md` "API 계약 변경 방침"). 다만 바꿨으면 스펙과 `refactor-log.md`에 Before/After를 남긴다 — 5인 공용 저장소라 팀원이 나중에 놀라지 않게. `Breaking-Change-Reason:` 트레일러는 이 저장소에서 쓰지 않는다(`core-guardrails.md` STOP §5는 옵션 꺼짐)

## Configuration

- **프로필 파일을 두지 않는다.** baro-ai는 `application.yml` 하나로 돌고, 환경 차이는 전부 환경 변수 기본값(`${VAR:기본값}`)으로 흡수한다(`application-prod.yml`은 2026-09-05에 삭제).
- 기능별 설정은 별도 yml로 분리하고 `@ConfigurationProperties`로 받는다 — `recipe-recommend.yml`·`review-sentiment.yml`·`review-negative-keywords.yml`
- 민감 정보: 환경 변수 — `application*.yml`에 하드코딩 금지

## Tests

- JUnit 5, `{ClassName}Test`
- `src/test/java/` 패키지는 main과 **동일한 도메인·레이어 구조** 유지
- **대부분 순수 단위 테스트다** — Mockito로 저장소·클라이언트를 mock한다. `@SpringBootTest`는 쓰지 않는다.
- **Elasticsearch는 예외로 실물을 띄운다**(2026-09-08 도입). `ProductIndexAndSearchIntegrationTest`가 Testcontainers로 `docker/local/Dockerfile.es-nori`를 빌드해 매핑·형태소 분석·자동완성 접두어·벡터 후보 조회를 확인한다. mock으로는 "무엇을 호출했는가"까지만 고정되고 매핑이 실제로 만들어지는지는 알 수 없기 때문이다. Docker가 없으면 이 클래스만 실패한다.
- Kafka·Gemini는 아직 실물로 확인하지 않는다 — 남은 몫은 `audits/open-items.md`에 있다.
- 대상 기준은 "인프라 없이 돌고, 깨지면 사용자에게 보이는 것" — 순수 계산(가중치·정규화·다양화)과 서비스 경계(이유 코드, 호출 횟수, 폴백). 상세: [`testing.md`](testing.md)
- Kafka·Gemini가 필요한 통합 검증은 아직 자동화돼 있지 않다. 로컬에 실물을 띄워 사람이 확인하고(`baro-ai/docker/local/`), 실행하지 못했으면 "미실행"으로 남긴다(`audits/open-items.md`).
