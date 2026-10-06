# Cart-recipe Architecture Audit 001 — 장바구니 연동·요청 경로 (2026-09-04)

장바구니 레시피 작업 단위에서 **장바구니 조회(Feign) → 보유 재료 조회 → 레시피 후보 생성 호출 → 부족 재료 상품 검색 → 응답**까지의 요청 경로를 감사한 결과다. 프롬프트 내용·모델 옵션([`recommend/001`](../recommend/001-llm-prompt-tuning.md))과 채점 정책([`recommend/002`](../recommend/002-recipe-scoring.md))은 이미 감사했으므로 여기서 다시 다루지 않는다. 반드시 고칠 A 항목은 **2건**(prod에서 `shopping-service` 이름이 해석되지 않음, Feign 응답 envelope 언래핑 누락 — 둘 중 하나만 있어도 실제 사용자 경로는 항상 빈 응답이다), 유지보수성 B 항목은 **5건**이다. 코드는 수정하지 않았고 **2026-09-05 승인 완료**(문서 끝 "승인 결과" 절).

## 범위

- 대상 코드: `com.barofarm.ai.recommend`의 `application.RecipeRecommendService`·`application.config.RecommendProperties`·`application.dto.*`·`domain.*`·`infrastructure.client.**`(`CartClient`·`CartFeignConfig`·`CartFeignErrorDecoder`·`dto.*`)·`infrastructure.llm.RecipeCandidateGenerator`(호출·파싱 구조만, 프롬프트 문구 제외)·`presentation.RecommendationController`(레시피 엔드포인트 2개)·`exception.RecommendErrorCode`
- 대상 설정: `src/main/resources/recipe-recommend.yml`·`application.yml`·`application-prod.yml`·`build.gradle`
- 읽기만 한 상대편: `baro-shopping`의 `CartController`·`CartService`·`CartInfo`·`CartItemInfo`, `baro-common`의 `ResponseDto`, `k8s/apps/baro-ai/*.yml`·`k8s/apps/common/infra-configmap.yml`·`k8s/apps/baro-shopping/service.yml`, `baro-gateway`의 `application-prod.yml`, `baro-order`의 Feign 설정(참고용)
- 감사자: 메인 세션이 넘긴 가설 12개를 신선한 컨텍스트에서 코드로 독립 검증했다 — `safe-refactor` 1단계. 라이브러리 동작은 추측하지 않고 실제 해석된 jar(`./gradlew :baro-ai:dependencies --configuration runtimeClasspath`)의 바이트코드를 `javap`으로 열어 확인했다
- 기준: `audit-checklist.md` 1~14항목 + 16번은 호출·파싱 구조만, `core-guardrails.md` ⛔ STOP
- `baro-ai-performance-refactor.md`가 활성화된 동안에는 근거 기준(적극적 증거 필수)이 이 문서 전체에 적용된다 — 아래 A 항목의 "근거" 필드는 생략할 수 없다
- 이번 라운드에서 하지 않은 것(사용자 결정): 단위 테스트 추가 제안, Redis, `CompletableFuture` 병렬화, 다른 모듈 코드 변경
- `researcher`는 호출하지 않았다 — "참고: Best Practice·유사 구현 사례" 절은 생략한다

**범위 밖 (앞선 감사가 이미 다뤘거나 다른 소유)**

| 주제 | 어디서 다뤘나 | 이번 라운드 |
|------|---------------|-------------|
| 프롬프트 문구·길이·few-shot·모델 tier·temperature | [`recommend/001`](../recommend/001-llm-prompt-tuning.md) C-1·D-1~D-3 | 다루지 않음. 단 그 문서 B-1(`CandidateRecipePlan` 주석 2줄)은 아직 코드에 반영되지 않은 것을 확인했고(`CandidateRecipePlan.java:15,17`) 아래 B-3에 흡수한다 |
| 후보 채점식·가중치·동점 처리 | [`recommend/002`](../recommend/002-recipe-scoring.md) C-1 | 다루지 않음(사용자 검토 대기) |
| Gemini 재시도·응답 스키마 강제·관측 지표 | [`cross-cutting/001`](../cross-cutting/001-llm-call-infra.md) A-1·A-2·B-1(반영 완료) | 다루지 않음. 스키마가 서버 계산 필드까지 강제한다는 후속 후보만 아래 B-3으로 가져온다 |
| `review` 도메인 | 팀원 소유 | 코드·문서 모두 건드리지 않음 |

**가설 ↔ 판정 대응표** (메인 세션이 넘긴 H1~H12)

| 가설 | 판정 | 항목 |
|------|------|------|
| H1 Feign 응답 언래핑 누락 | **A** | A-1 |
| H2 prod에서 `shopping-service` 해석 불가 | **A** | A-2 |
| H3 `CartFeignErrorDecoder` 404 분기·`CART_NOT_FOUND` dead | **B** | B-1 (나머지 6개 상수는 C-5) |
| H4 테스트용 POST 엔드포인트 | **B** | B-2 |
| H5 LLM 스키마에 서버가 덮어쓰는 필드 포함 | **B** | B-3 |
| H6 `@Data` + 8필드 수동 복사 | **B** | B-3 (H5와 한 항목) |
| H7 `ProductSearchRepository` 도메인 경계 침범 | **C** | C-1 |
| H8 게이트웨이 5초 vs LLM 최악 약 93초 | 판단 항목 | 판단 1 |
| H9 부족 재료 검색 순차 실행 | **D** | D-1 |
| H10 카테고리 폴백 문자열 | **C** | C-2 |
| H11 광범위 `catch (Exception)` | **D** | D-2 (로그 문구만 B-4) |
| H12 수동 생성자 + `@Qualifier` | **D** | D-3 |

**확인한 실물** (버전은 `runtimeClasspath` 해석 결과)

| 라이브러리 | 확인한 사실 |
|-----------|-------------|
| `spring-cloud-openfeign-core` 4.3.0 | `FeignClientProperties` prefix는 `spring.cloud.openfeign.client`, 클라이언트별 설정에 `url`·`connectTimeout`·`readTimeout` 필드 존재. 기본 `Decoder`는 `OptionalDecoder(ResponseEntityDecoder(SpringDecoder(HttpMessageConverters)))`. `FeignClientFactoryBean.getTarget`은 `url`이 비어 있고 설정에도 없으면 `loadBalance(...)`로 간다. `FeignBlockingLoadBalancerClient.execute`는 인스턴스가 없으면 예외가 아니라 **503 `Response`**를 돌려준다("Load balancer does not contain an instance for the service …"). HTTP 클라이언트 자동설정 순서는 OkHttp → HttpClient5 → Http2 → Default |
| `spring-cloud-commons` 4.3.0 | `SimpleDiscoveryClientAutoConfiguration`은 다른 `DiscoveryClient`가 없을 때 `spring.cloud.discovery.client.simple.*`로 인스턴스 목록을 만든다 |
| `spring-cloud-netflix-eureka-client` 4.3.0 | `EurekaClientAutoConfiguration`은 `eureka.client.enabled`(기본 true)로 켜고 끈다 |
| `spring-web` 6.2.14 · `spring-boot-autoconfigure` 3.5.8 · `jackson-databind` 2.19.4 | `JacksonAutoConfiguration.jacksonObjectMapper(Jackson2ObjectMapperBuilder)` → `Jackson2ObjectMapperBuilder.customizeDefaultFeatures`가 `FAIL_ON_UNKNOWN_PROPERTIES=false`를 건다. 그 `ObjectMapper`로 `MappingJackson2HttpMessageConverter`가 만들어지고 위 `SpringDecoder`가 그걸 쓴다. baro-ai yml에 `spring.jackson.*` 재정의 없음 |
| `feign-core` 13.6 · `feign-okhttp` 13.6 | `Request.Options()` 기본값 연결 10초 / 읽기 60초. 런타임 classpath에 `feign-hc5`가 **없어** 실제 HTTP 클라이언트는 OkHttp다(`OkHttpFeignLoadBalancerConfiguration`의 `@ConditionalOnClass(feign.okhttp.OkHttpClient)` 충족, HttpClient5 쪽은 `feign.hc5.ApacheHttp5Client` 부재로 불충족) |
| `spring-ai-model` 1.1.8 | `BeanOutputConverter.generateSchema`가 `withRequiredCheck(field -> true)`를 걸어 **모든 필드를 required**로 만들고 `FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT`를 켠다 |
| `lombok` 1.18.46 | `HandlerUtil`의 기본 복사 대상 어노테이션 목록은 `NonNull` 계열뿐. 저장소에 `lombok.config` 없음 |

## ✅ A. 반드시 수정해야 하는 사항

**두 항목의 관계**: prod에서는 A-2(이름 해석 불가)가 먼저 터져 장바구니 호출 자체가 503으로 끝나고, A-2를 고치면 그다음 A-1(언래핑 누락)이 터져 여전히 빈 응답이 나온다. **둘 다 고쳐야 실제 사용자 경로가 처음으로 동작한다.** 테스트용 POST 엔드포인트(B-2)는 `CartInfo`를 body로 직접 받아 두 문제를 모두 우회하므로, 그 경로로만 확인해 왔다면 지금까지 드러나지 않았을 수 있다.

### A-1. Feign이 `ResponseDto` envelope를 벗기지 않아 장바구니가 항상 비어 보임

- **Priority**: High
- **Category**: Architecture (잠재적 버그 — 코드로 확정, 런타임 미검증)
- **문제**: `baro-shopping`의 `GET /api/v1/carts`는 `ResponseDto<CartInfo>`(`{status, data, message}`)를 돌려주는데, baro-ai의 `CartClient.getCart`는 반환 타입을 `CartInfo`로 선언했다. Feign 기본 디코더가 envelope의 `status`·`data`·`message`를 모르는 속성으로 **조용히 버리고** `CartInfo`의 6개 필드를 전부 `null`로 채우므로, `cart.items() == null` 분기에 걸려 "장바구니가 비어있습니다"로 빈 응답이 나간다
- **근거**:
  - 상대편 계약: `baro-shopping/.../CartController.java:33-40` — `public ResponseDto<CartInfo> getCart(...)` / `return ResponseDto.ok(cartInfo)`. `baro-common/.../ResponseDto.java:5-12` — 필드는 `int status`·`T data`·`String message`
  - 우리 쪽 선언: `CartClient.java:15-16` — `@GetMapping CartInfo getCart(@RequestHeader("X-User-Id") UUID userId)`. `CartInfo.java:8-15`(baro-ai 사본)에는 `status`·`data`·`message`에 해당하는 필드가 없다
  - 커스텀 `Decoder` 없음: `grep -rn "feign.codec.Decoder\|ObjectMapper" baro-ai/src/main/java` 0건. `CartFeignConfig.java:7-13`은 `ErrorDecoder`만 등록한다
  - 기본 디코더가 모르는 속성을 무시한다는 사실: 위 "확인한 실물" 표 — `SpringDecoder` → Boot 자동설정 `MappingJackson2HttpMessageConverter` → `Jackson2ObjectMapperBuilder`가 `FAIL_ON_UNKNOWN_PROPERTIES=false`. baro-ai yml에 `spring.jackson.*` 재정의 없음(`grep -rn jackson baro-ai/src/main/resources` 0건). Jackson 2.19.4는 record를 canonical 생성자로 만들고, 없는 속성은 `null`로 넘긴다
  - 빈 응답으로 떨어지는 분기: `RecipeRecommendService.java:58-61` — `cart == null || cart.items() == null || cart.items().isEmpty()` → WARN + 빈 `RecipeRecommendResponse`
  - 스펙도 이미 리스크로 기록: [`recipe-recommendation.md`](../../specs/cart-recipe/recipe-recommendation.md) 108·112·124줄
  - **불확실한 것**: 실제 HTTP 왕복은 이번 세션에서 실행하지 못했다(baro-shopping·MySQL 기동 필요). 위 근거 사슬은 전부 코드·jar로 확인한 것이고, 빠진 고리는 없다
- **왜 문제인가**: 실제 사용자 경로(`GET /recipes/{userId}`)가 **항상** 빈 결과를 돌려준다. 실패가 200 + 빈 값으로 나가는 fail-safe 설계라 어디서도 에러로 보이지 않고, 로그에는 "장바구니가 비어있습니다"라는 사실과 다른 메시지만 남는다
- **개선 방법**: `CartClient.getCart`의 반환 타입을 `ResponseDto<CartInfo>`로 바꾸고, `RecipeRecommendService.recommendFromCartWithMissing`에서 `.data()`를 꺼내 `recommendFromCart`에 넘긴다(`data`가 `null`이면 기존 `cart == null` 분기가 그대로 처리). 커스텀 `Decoder`는 만들지 않는다(D-4). `ResponseDto`는 `baro-common`에 이미 있어 새 타입이 필요 없다
- **검증 방법** (로컬 수동): `docker-compose.data.yml`로 인프라를 띄우고 `baro-shopping`과 baro-ai를 함께 기동한다(A-2의 `SHOPPING_SERVICE_URL`을 로컬 주소로). 사용자 한 명의 장바구니에 상품을 담은 뒤 `curl localhost:8092/api/v1/recommendations/recipes/{userId}`를 호출한다. **Before**: WARN `장바구니가 비어있습니다. userId: …` + `ownedIngredients: []`. **After**: 그 WARN이 사라지고 `ownedIngredients`에 재료명이 채워진다(재료명이 비어 있으면 `loadOwnedIngredientNames`의 "재색인이 필요할 수 있습니다" WARN이 대신 나오는데, 이건 A-1과 무관한 색인 상태 문제다)
- **API 영향**: No Impact — baro-ai가 밖으로 내는 `RecipeRecommendResponse`는 그대로. 바뀌는 건 내부 Feign 인터페이스 시그니처뿐
- **예상 변경 파일**: `recommend/infrastructure/client/CartClient.java`, `recommend/application/RecipeRecommendService.java`, `docs/specs/cart-recipe/recipe-recommendation.md`(리스크 표·검증 시나리오 체크박스)
- **예상 변경 라인 수**: 코드 4줄 내외 + 스펙 3줄
- **위험도**: Low
- **테스트 영향도**: 자동화 테스트 없음(사용자 결정) — 위 수동 절차로 대체
- **예상 효과**: 기능이 실제 경로에서 처음으로 동작

### A-2. `@FeignClient(name = "shopping-service")`가 prod에서 어디로도 가지 못함

- **Priority**: High
- **Category**: Architecture (배포 설정 — baro-ai 자신의 yml로 해결 가능)
- **문제**: `CartClient`는 서비스 이름만 적고 `url`이 없다. prod는 Eureka가 꺼져 있고, 대체 수단(`spring.cloud.discovery.client.simple.instances`, `spring.cloud.openfeign.client.config.shopping-service.url`)도 없으며, k8s Service 이름은 `shopping-service`가 아니라 `baro-shopping`이다. 따라서 로드밸런서가 인스턴스를 찾지 못해 **503**을 만들고, 그게 `CartFeignErrorDecoder`의 default 분기 → `CustomException(FEIGN_ERROR)` → `catch (Exception)` 순으로 흡수돼 빈 응답이 된다
- **근거**:
  - `CartClient.java:9-13` — `name = "shopping-service"`, `path`·`configuration`만 있고 `url` 없음
  - `application-prod.yml:58-60` — `eureka.client.enabled: false`. `EurekaClientAutoConfiguration`은 이 값으로 꺼진다(jar 확인)
  - `grep -rn "openfeign\|discovery\|simple" baro-ai/src/main/resources/` 0건 — Feign `url` 설정도, simple discovery 인스턴스 목록도 없다
  - `k8s/apps/baro-shopping/service.yml:4,12-13` — Service 이름 `baro-shopping`, 포트 8080(→ 컨테이너 8082). `k8s/apps/baro-ai/deployment.yml:27-36`과 `k8s/apps/common/infra-configmap.yml`에 shopping 주소를 주입하는 항목 없음
  - 같은 클러스터에서 다른 모듈은 이름 대신 주소를 박아 쓴다: `baro-gateway/.../application-prod.yml:113-114` — `uri: http://baro-shopping:8080`. `baro-order/.../application-prod.yml:42-46` — `feign.client.config.shopping-service.url: ${SHOPPING_SERVICE_URL:http://baro-shopping:8080}`(참고: 이 키는 Spring Cloud OpenFeign 4.x의 prefix `spring.cloud.openfeign.client`와 다르다 — baro-order 소유이므로 여기서는 관찰만, 판단 항목 2)
  - 실패 경로(jar): `FeignClientFactoryBean.getTarget` → `url` 비어 있음 → `loadBalance(...)`; `DiscoveryClient`는 `SimpleDiscoveryClient`(인스턴스 0개); `FeignBlockingLoadBalancerClient.execute` → 인스턴스 없음 → `HttpStatus.SERVICE_UNAVAILABLE`로 `Response` 생성 → `CartFeignErrorDecoder.java:18-20` default → `CustomException(CommonErrorCode.FEIGN_ERROR)` → `RecipeRecommendService.java:46-49` catch → 빈 응답. 로그에는 `"Feign 통신 오류입니다."`만 남는다
  - 로컬도 같다: `application.yml:10-11`은 기본 프로필을 `local`로 두지만 `application-local.yml`이 없고, [`IMPLEMENTATION.md`](../../IMPLEMENTATION.md) 89줄의 로컬 실행 절차는 `SPRING_PROFILES_ACTIVE=prod`를 내보내라고 안내한다 — 즉 로컬에서도 prod 프로필로 도는데 `shopping-service`를 풀어 줄 것이 없다. 저장소의 `docker-compose.data.yml`·`docker/`에도 Eureka는 없다(`grep -rin eureka` 0건)
  - **불확실한 것**: 실제 클러스터에서의 관찰은 없다(서버 내려감). 위 사슬은 코드·매니페스트·jar로 닫혀 있다
- **왜 문제인가**: A-1과 함께, 기능이 배포 환경에서 한 번도 동작한 적이 없을 가능성이 높다. 이름 해석 실패가 503 → 예외 → 빈 응답으로 세 번 포장되면서, 로그만 봐서는 "shopping이 죽었다"와 "주소를 모른다"를 구분할 수 없다
- **개선 방법**: baro-ai의 `application-prod.yml`에 아래를 추가한다(다른 모듈·k8s 파일은 건드리지 않는다).
  ```yaml
  spring:
    cloud:
      openfeign:
        client:
          config:
            shopping-service:
              url: ${SHOPPING_SERVICE_URL:http://baro-shopping:8080}
  ```
  기본값은 게이트웨이가 쓰는 주소와 같게 두고, 로컬 개발자는 `SHOPPING_SERVICE_URL=http://localhost:8082`(baro-shopping 컨테이너 포트 기준)로 덮어쓴다. `IMPLEMENTATION.md` 로컬 실행 절차에 이 환경변수를 한 줄 추가한다. 이 방식이면 `FeignClientFactoryBean`이 로드밸런서를 타지 않고 주소로 바로 간다. 네임스페이스가 다르면 FQDN이 필요한데 그건 배포 담당자 확인 대상이다(판단 항목 2)
- **검증 방법** (로컬 수동): baro-shopping을 띄우고 baro-ai를 `SPRING_PROFILES_ACTIVE=prod SHOPPING_SERVICE_URL=http://localhost:8082`로 기동 → 추천 API 1회 호출. **Before**: WARN `장바구니 조회 실패로 레시피 추천을 건너뜁니다. … error: Feign 통신 오류입니다.` **After**: 그 WARN이 사라지고(A-1이 아직이면 "장바구니가 비어있습니다"로 바뀜), baro-shopping 로그에 `GET /api/v1/carts` 요청이 찍힌다. 기동 자체가 되는지도 이 절차로 같이 확인된다
- **API 영향**: No Impact
- **예상 변경 파일**: `src/main/resources/application-prod.yml`, `docs/IMPLEMENTATION.md`, `docs/specs/cart-recipe/recipe-recommendation.md`(리스크 표)
- **예상 변경 라인 수**: yml 7줄 + 문서 3줄
- **위험도**: Low — 잘못된 주소를 넣어도 지금과 같은 빈 응답으로 떨어지고, 로그 메시지는 오히려 명확해진다
- **테스트 영향도**: 없음 — 위 수동 절차로 대체
- **예상 효과**: 기능이 배포 환경에서 동작 · 장애 원인 로그가 구체화

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `CartFeignErrorDecoder`·`CartFeignConfig`·`RecommendErrorCode.CART_NOT_FOUND` 삭제 — 404 분기는 죽어 있고, 나머지는 오히려 정보를 지움

- **Priority**: Medium
- **Category**: Dead Code
- **문제**: 디코더의 404 분기는 도달할 수 없고, default 분기는 모든 실패를 `"Feign 통신 오류입니다."` 한 문장으로 바꿔 놓아 상태 코드·URL이 로그에서 사라진다. 어차피 호출부가 모든 예외를 잡으므로(D-2) 디코더 유무로 동작은 달라지지 않는다
- **근거**:
  - 404가 안 나온다: `baro-shopping/.../CartService.java:41-53` — `getCart`는 장바구니가 없으면 `CartInfo.empty()`를 돌려준다. `CART_NOT_FOUND`(404)는 `findCart`(`:210-217`)에서만 던지고, 그건 수량 변경·삭제 계열이 쓴다. `CartController.java:33-40`의 GET은 `getCart`만 부른다
  - `CartFeignErrorDecoder.java:14-16` — 404 → `RecommendErrorCode.CART_NOT_FOUND`. `grep -rn "RecommendErrorCode\." baro-ai/src/main/java`는 이 한 곳뿐이다
  - 정보 손실: `RecipeRecommendService.java:48`은 `e.getMessage()`만 로그로 남긴다. `CustomException(FEIGN_ERROR)`의 메시지는 `CommonErrorCode.java:14`의 고정 문구다. 디코더가 없으면 Feign 기본 예외가 들어오고, 그 메시지에는 상태 코드·메서드·URL이 들어간다(A-2의 503도 "shopping-service" 이름과 함께 보였을 것이다)
  - `CartFeignConfig.java:7`은 `@Configuration`이고 컴포넌트 스캔 범위(`AiApplication.java:8`, `com.barofarm.ai`) 안에 있어 이 `ErrorDecoder`가 모든 Feign 클라이언트의 전역 빈이 된다. 지금은 Feign 클라이언트가 하나뿐이라 실질 영향은 없지만, 삭제하면 이 함정도 함께 사라진다
- **왜 문제인가**: 존재하지 않는 실패를 위한 코드가 3파일에 걸쳐 있고, 실제로 하는 일은 진단 정보를 줄이는 것뿐이다. STOP §4(정책 불일치·호출되지 않는 분기 즉시 삭제)에 해당한다
- **개선 방법**: `CartFeignErrorDecoder.java`·`CartFeignConfig.java` 삭제, `CartClient`의 `configuration = CartFeignConfig.class` 제거, `RecommendErrorCode.CART_NOT_FOUND`와 그 위 주석(`:19-20`) 삭제. 스펙 리스크 표의 "`RecommendErrorCode`는 선언만 있고 던지지 않음" 행을 반영 결과로 갱신(STOP §2 — ErrorCode 변경과 스펙 갱신은 같은 턴)
- **검증 방법**: 컴파일 + A-2의 Before 절차를 `SHOPPING_SERVICE_URL`을 일부러 틀린 주소로 두고 1회 호출 → WARN의 `error:` 뒤에 상태 코드·URL이 보이는지 확인
- **API 영향**: No Impact — 이 ErrorCode는 외부로 나간 적이 없다(항상 catch됨)
- **예상 변경 파일**: `recommend/infrastructure/client/CartFeignErrorDecoder.java`(삭제), `CartFeignConfig.java`(삭제), `CartClient.java`, `recommend/exception/RecommendErrorCode.java`, `docs/specs/cart-recipe/recipe-recommendation.md`
- **예상 변경 라인 수**: −35줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 코드 감소 · 장애 로그 정확도

### B-2. 테스트용 `POST /api/v1/recommendations/recipes/test` 정리

- **Priority**: Medium
- **Category**: Cleanup (API 계약 변경)
- **문제**: 운영 컨트롤러에 "테스트용"이라고 적힌 엔드포인트가 살아 있다. 인증 없이 임의 `CartInfo`를 넣어 LLM 호출(비용·지연)을 일으킬 수 있고, 문서 두 곳이 이걸 정식 API처럼 소개한다
- **근거**: `RecommendationController.java:47-57` — `summary = "… (테스트용)"`, `@PostMapping("/recipes/test")`, `@RequestBody CartInfo`. `RecipeRecommendService.java:53-55` — `testRecommendFromCartWithMissing`. 문서: [`FEATURES.md`](../../FEATURES.md) 195-197줄이 요청 본문 예시까지 싣고, [`docs/README.md`](../../README.md) 77줄이 "테스트용"으로 나열. 호출부는 이 두 곳뿐(`grep -rn "recipes/test\|testRecommendFromCartWithMissing"`)
- **왜 문제인가**: 이 엔드포인트가 A-1·A-2를 우회하는 유일한 경로였기 때문에 "기능이 된다"는 착각을 만들 수 있었다. A-1·A-2가 반영되면 실제 경로로 확인할 수 있으므로 우회 경로를 유지할 이유가 약해진다
- **개선 방법** (사용자 선택 — 판단 항목 3):
  - (a) **삭제** — 컨트롤러 메서드·서비스 메서드·`FEATURES.md` 절·`README.md` 항목·스펙 API 표 행을 같은 변경에서 제거(STOP §4). 권장
  - (b) baro-shopping 없이 LLM 경로를 수동 확인할 손잡이가 여전히 필요하다면, 별도 `@Profile("!prod")` 컨트롤러 클래스로 옮긴다. 다만 로컬도 prod 프로필로 도는 현재 절차(`IMPLEMENTATION.md:89`)에서는 이 방법이 로컬에서도 숨겨져 실효가 없으므로, 택한다면 로컬 프로필 정리가 선행돼야 한다
- **검증 방법**: (a) 기동 후 `POST /api/v1/recommendations/recipes/test` → 404, `/v3/api-docs`에서 해당 path 사라짐 확인
- **API 영향**: **Breaking** — Before: `POST /api/v1/recommendations/recipes/test`(body `CartInfo`) 존재 / After: 없음. 프론트·다른 모듈이 부르는 곳은 저장소 전체 grep 기준 0건. `refactor-log.md`에 Before/After를 남긴다(`baro-ai-performance-refactor.md` API 계약 변경 방침)
- **예상 변경 파일**: `recommend/presentation/RecommendationController.java`, `recommend/application/RecipeRecommendService.java`, `docs/FEATURES.md`, `docs/README.md`, `docs/specs/cart-recipe/recipe-recommendation.md`
- **예상 변경 라인 수**: 코드 −15줄, 문서 −40줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 운영 노출 면 축소 · 문서-구현 정합

### B-3. LLM 응답 전용 record 분리 + `CandidateRecipePlan` 불변화 — 스키마·프롬프트·복사 코드가 함께 준다

- **Priority**: Medium
- **Category**: LLM-Prompt (호출 구조) + Readability
- **문제**: LLM에 강제하는 JSON 스키마가 서버가 곧바로 덮어쓰는 두 필드(`usedOwnedIngredients`·`missingIngredientsCore`)까지 **required**로 요구하고, 프롬프트는 그 두 필드를 "빈 배열로 두라"고 따로 지시하며, 서버는 응답 객체를 8개 setter로 손으로 복사해 새 객체를 만든다. 모두 "응답 타입 = 도메인 타입"으로 겸용한 데서 나온 비용이다
- **근거**:
  - 스키마 강제: `RecipeCandidateGenerator.java:26-27` — `BeanOutputConverter<RecipeCandidates>`; `:117-120` — `responseSchema(RESPONSE_CONVERTER.getJsonSchema())`. `spring-ai-model` 1.1.8 `BeanOutputConverter.generateSchema`는 `withRequiredCheck(field -> true)`(바이트코드 `lambda$generateSchema$0`이 `iconst_1; ireturn`)로 **모든 필드를 required**로 만든다. `CandidateRecipePlan.java:23-24`의 두 필드가 그 대상에 들어간다 → 후보 3개 × 빈 배열 2개를 모델이 반드시 출력해야 한다
  - 프롬프트 지시: `RecipeCandidateGenerator.java:106` — `usedOwnedIngredients, missingIngredientsCore: 빈 배열로 두세요. 서버가 다시 계산합니다.`
  - 서버가 덮어씀: `:159-195 enhanceWithServerValidation` — `:183-193`에서 `new CandidateRecipePlan()` 후 setter 8개로 복사하고 두 필드만 계산값으로 채운다
  - 가변 도메인: `CandidateRecipePlan.java:11-12` `@Data @NoArgsConstructor`; `RecipeCandidates.java:15-16` `@Data`에 더해 `:27-29`에 손으로 쓴 `candidates()` 접근자가 `getCandidates()`와 중복
  - 주석 불일치(recommend/001 B-1, 미반영): `CandidateRecipePlan.java:15,17`은 여전히 `"볶음"`·`"점심"` 예시. `RecipeCandidateGenerator.java:31-35`의 `LLM #2`·"한식/일식/양식 각 1개씩"은 3회 호출 시절의 잔재이고 프롬프트(`:85`)와도 다르다
  - 후속 후보로 이미 기록: [`cross-cutting/refactor-log.md`](../cross-cutting/refactor-log.md) "남긴 것 · 알려진 제약"
- **왜 문제인가**: 출력 토큰은 입력보다 비싸고 지연에 직접 반영되는데, 버릴 값을 매 요청 6개씩 만들게 한다. 유지보수 관점에서는 필드를 하나 추가하면 프롬프트·스키마·복사 코드 세 곳을 같이 고쳐야 하고, 빠뜨리면 조용히 `null`로 복사된다(지금도 setter 복사가 필드 추가를 자동으로 따라가지 않는다)
- **개선 방법**:
  1. `infrastructure.llm`에 LLM 출력 계약 record를 둔다 — `RecipeCandidateDraft`(cookingMethod, difficulty, mealType, recipeName, recipeIngredientsCore, recipeIngredientsExtra, instructions, staplesAssumed 8개)와 `RecipeCandidateDrafts(List<RecipeCandidateDraft> candidates)`. `BeanOutputConverter`는 이 타입으로 만든다
  2. `domain.CandidateRecipePlan`을 record(10개 필드, 불변)로 바꾸고 정적 팩터리 `from(RecipeCandidateDraft draft, List<String> ownedIngredients)`에 지금의 `enhanceWithServerValidation` 계산을 옮긴다. 리스트는 `IngredientProcessingUtil.normalizeList`(null-safe)로 받아 `calculateScore`와 같은 기준으로 맞춘다
  3. 프롬프트 `:106` 줄 삭제(STOP §4 — 스키마에서 빠진 필드에 대한 지시는 남기지 않는다), `RecipeCandidates`의 중복 접근자·`@Data` 정리, 위 주석 4곳 정정
  4. `chooseBest`·`calculateScore`의 계산식은 한 글자도 바꾸지 않는다(절대 원칙 2 — 채점 정책은 `recommend/002` 소관)
- **검증 방법** (로컬 수동): 기동 시 `RESPONSE_CONVERTER.getJsonSchema()`를 DEBUG로 한 번 찍어 후보 객체의 `properties`가 10개 → 8개, `required`에서 두 필드가 빠졌는지 확인. 같은 장바구니로 추천 API를 3회 호출해 `curl localhost:8092/actuator/metrics/gen_ai.client.token.usage`의 output 토큰 합계를 Before/After 비교(cross-cutting B-1로 이미 노출됨). 응답 `missingCoreIngredients`·`ownedIngredients`가 Before와 동일한지 눈으로 대조
- **API 영향**: No Impact — `RecipeRecommendResponse` 불변
- **예상 변경 파일**: `recommend/infrastructure/llm/RecipeCandidateGenerator.java`, `recommend/infrastructure/llm/RecipeCandidateDraft.java`(신규)·`RecipeCandidateDrafts.java`(신규), `recommend/domain/CandidateRecipePlan.java`, `recommend/domain/RecipeCandidates.java`, `docs/specs/cart-recipe/recipe-recommendation.md`(데이터 모델 표 "LLM 응답 스키마" 행)
- **예상 변경 라인 수**: +35 / −45줄 내외
- **위험도**: Medium — 스키마가 바뀌므로 실호출 1회로 파싱을 반드시 확인한다. victools 스키마 생성기가 record를 다루는지도 이 호출로 함께 확인된다(안 되면 record 대신 불변 클래스로 대체)
- **테스트 영향도**: 없음 — 위 수동 절차로 대체
- **예상 효과**: 출력 토큰 소폭 감소 · 필드 추가 시 고칠 곳 3 → 1 · 불변 도메인

### B-4. 잔재·군더더기 정리 (dead code · 부정확한 로그)

- **Priority**: Low
- **Category**: Cleanup
- **문제**: 요청 경로에 쓰이지 않는 코드와, 사실과 다른 로그 문구가 남아 있다
- **근거**:
  - `CartInfo.empty()`(baro-ai 사본, `CartInfo.java:16-25`) — `grep -rn "CartInfo.empty" baro-ai/src/main/java` 0건. shopping 쪽 원본에서 같이 복사돼 온 흔적
  - `RecipeRecommendService.java:45` — `userId != null ? userId.toString() : "null"`. `@PathVariable UUID userId`(`RecommendationController.java:66`)는 `null`이 될 수 없다
  - `RecipeRecommendService.java:146` — `.limit(size)`. 같은 `size`로 이미 `PageRequest.of(0, size)`(`:132`)를 넘겼다
  - `RecipeRecommendService.java:43-50` — try가 `recommendFromCart` 전체(ES 조회·LLM·상품 검색)를 감싸는데 catch 로그는 `"장바구니 조회 실패로 레시피 추천을 건너뜁니다"`다. ES가 죽어 `loadOwnedIngredientNames`(`:180`)에서 예외가 나도 "장바구니 조회 실패"로 기록된다
- **왜 문제인가**: 개별로는 사소하지만, 장애 때 로그 문구가 원인을 가리키지 않는 것은 A-1·A-2 같은 문제를 늦게 발견하게 만든 요인과 같은 종류다
- **개선 방법**: 앞 세 개 삭제. 로그는 catch 범위를 유지한 채(D-2) 문구를 "레시피 추천 실패(장바구니 조회·재료 조회·후보 생성 중 예외)"처럼 실제 범위로 고친다. `baro-shopping` 사본 DTO 주석(`CartInfo.java:7`, `CartItemInfo.java:5`)의 "Buyer Service"도 현재 모듈명 `baro-shopping`으로
- **검증 방법**: 컴파일. 로그 문구는 `SHOPPING_SERVICE_URL`을 틀린 주소로 두고 1회 호출해 새 문구가 찍히는지 확인
- **API 영향**: No Impact
- **예상 변경 파일**: `recommend/infrastructure/client/dto/CartInfo.java`, `CartItemInfo.java`, `recommend/application/RecipeRecommendService.java` (같은 패키지의 빈 record `SimilarProductResponse` 삭제는 [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) B-1이 SSOT — 여기서 중복 등재하지 않는다)
- **예상 변경 라인 수**: −20줄 내외
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 코드 감소 · 로그 정확도

### B-5. 장바구니 Feign 호출에 타임아웃이 없음 — 기본값 읽기 60초

- **Priority**: Medium
- **Category**: Performance (Resilience)
- **문제**: 동기 요청 경로의 첫 원격 호출인데 시간 상한을 정한 곳이 없다. 기본값은 연결 10초·읽기 60초라, baro-shopping이 응답을 안 주면 추천 요청 스레드가 최대 60초 붙잡힌다
- **근거**:
  - 설정 없음: `grep -rn "openfeign\|connectTimeout\|readTimeout" baro-ai/src/main/resources/` 0건. `CartFeignConfig.java`에도 `Request.Options` 빈 없음
  - 기본값: `feign-core` 13.6 `Request.Options()` 생성자 바이트코드 — `10L SECONDS`, `60L SECONDS`. 클라이언트별 설정 필드 `connectTimeout`·`readTimeout`은 `spring-cloud-openfeign-core` 4.3.0 `FeignClientProperties.FeignClientConfiguration`에 있다
  - 비교 대상: LLM 호출은 호출당 30초 상한을 이미 명시했다([`cross-cutting/001`](../cross-cutting/001-llm-call-infra.md) A-1). 같은 경로의 앞 단계만 상한이 없다
  - 게이트웨이는 5초에 끊는다(`baro-gateway/.../application-prod.yml:28-30`) — 그 뒤에도 baro-ai 스레드는 60초까지 남는다
- **왜 문제인가**: baro-shopping이 느려지면(그쪽 `createCartInfo`는 항목마다 상품·재고를 재조회하는 N+1이라 `CartService.java:157-159` TODO에 스스로 적어 둔 상태다) 사용자는 이미 504를 받았는데 baro-ai는 그 뒤로도 스레드를 붙잡고 있다가, 뒤늦게 LLM까지 호출한다
- **개선 방법**: A-2와 같은 yml 블록에 `connectTimeout`·`readTimeout`을 명시한다. 값은 [미정] — 게이트웨이 5초와 판단 항목 1의 결론에 맞춘다. 잠정 제안은 연결 1초 / 읽기 3초. 값의 근거(게이트웨이 상한)를 yml 주석으로 남긴다
- **검증 방법** (로컬 수동): 터미널에서 `nc -l 8082`(연결은 받고 응답은 안 함)를 띄우고 `SHOPPING_SERVICE_URL=http://localhost:8082`로 추천 API 1회 호출, WARN이 찍힐 때까지의 시간을 잰다. **Before**: 약 60초. **After**: 설정한 읽기 타임아웃 근처
- **API 영향**: No Impact
- **예상 변경 파일**: `src/main/resources/application-prod.yml`
- **예상 변경 라인 수**: yml 3줄
- **위험도**: Low — 너무 짧게 잡으면 정상 장바구니도 실패로 떨어지므로 baro-shopping 정상 응답 시간을 먼저 재고 정한다
- **테스트 영향도**: 없음
- **예상 효과**: 최악 응답 시간 상한 확보

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. `recommend.application`이 `search.infrastructure.ProductSearchRepository`를 직접 주입 (H7)

`RecipeRecommendService.java:17,40,180`이 다른 도메인의 리포지터리를 건너 들어간다. `SimilarProductRecommendService`도 같은 방식이라 이 단위에서 생긴 패턴은 아니고, [`recommend/refactor-log.md`](../recommend/refactor-log.md) 143줄이 이미 후속 과제로 적어 뒀다. `RecipeRecommendService`는 이미 `ProductSearchService`를 주입받고 있으므로(`:37`) 거기에 `findAllById` 성격의 조회 메서드 하나를 두면 의존성이 하나 준다. **왜 지금 안 하나**: 런타임 동작이 바뀌지 않는 순수 레이어 정리이고, 같이 고쳐야 할 `SimilarProductRecommendService`는 임베딩 유사도 검색 단위 소관이다. C-2를 하기로 하면 같은 줄을 만지므로 그때 함께 하는 것이 자연스럽다.

### C-2. 장바구니 항목의 카테고리 코드가 한글 폴백 문자열로 올 수 있음 (H10)

`baro-shopping/.../CartItemInfo.java:31`은 상품 조회 실패 시 `productCategoryCode`에 `"(카테고리 코드를 불러오지 못했습니다)"`를 넣는다. `RecipeRecommendService.isRecipeEligible`(`:157-167`)은 이 문자열이 제외 목록에 없으므로 **통과**시킨다 — 즉 shopping 쪽 상품 조회가 실패한 과일 상품은 보유 재료로 들어갈 수 있다. 재료명 자체는 `productId`로 ES에서 읽으므로(`:180`) 재료명이 틀리지는 않고, 정책(과일 제외)만 그 한 건에서 새는 것이다. 이미 조회하는 `ProductDocument`에 `productCategoryCode`(`ProductDocument.java:38`)가 있어 baro-ai 자신의 색인 값으로 제외를 판정하면 폴백 문자열 문제가 사라지지만, 그러면 판정 근거가 "장바구니 스냅샷"에서 "ES 문서"로 바뀐다(색인이 늦으면 결과가 달라질 수 있다). **왜 지금 안 하나**: shopping 쪽 조회 실패가 실제로 일어난 관찰이 없고, 판정 근거를 바꾸는 것은 정책 표현의 변경이라 사용자 결정이 필요하다(판단 항목 6).

### C-3. 응답의 `ownedIngredients`가 비교용 정규화 형태로 나감

`loadOwnedIngredientNames`(`:182`)는 `normalizeForCompare`(소문자·공백 제거)를 거친 문자열을 모으고, 그게 그대로 응답 `ownedIngredients`(`:118`)와 프롬프트 입력(`:88`)이 된다. 재료명이 색인 시점 LLM이 만든 한 단어 한글이라 지금은 차이가 보이지 않지만, 공백이나 영문 대문자가 있는 재료명은 표시 형태가 바뀐다. 표시용 원문과 비교용 정규화를 분리하는 것이 정확하다. **왜 지금 안 하나**: 사용자에게 보이는 차이가 확인된 사례가 없고, 스펙(`비즈니스 규칙` "정규화 후 중복 제거")과도 어긋나지 않는다.

### C-4. `build.gradle`의 안 쓰는 의존성 후보 — `cross-cutting` 라운드로

이 단위의 요청 경로를 따라가며 확인한 것만 적는다. 판단은 공유 인프라 라운드에서 한다.

| 의존성 | 관찰 |
|--------|------|
| `spring-boot-starter-data-jpa`(`build.gradle:62`)·`mysql-connector-j`(`:91`) | `application-prod.yml:17-20`이 DataSource·JPA 자동설정을 제외하고, baro-ai에 JPA 엔티티가 없다(`harness-map.md` 스택 옵션) |
| `spring-cloud-starter-config`(`:65`) | `k8s/apps/baro-ai/deployment.yml:33-36`이 `SPRING_CLOUD_CONFIG_ENABLED=false`로 끈다 |
| `spring-cloud-starter-netflix-eureka-client`(`:64`) | prod에서 꺼져 있고(A-2), 로컬도 prod 프로필로 돈다. A-2로 Feign이 주소를 직접 쓰면 이 스타터를 쓰는 곳이 없어진다. 단 `@EnableDiscoveryClient`(`AiApplication.java:9`)와 함께 지워야 컴파일된다 |
| `feign-okhttp:13.6`(`:88`) | **지우면 안 된다.** `feign-hc5`가 classpath에 없어 OkHttp가 실제 HTTP 클라이언트다(위 "확인한 실물"). 버전 `13.6`은 BOM이 관리하는 값과 같아 명시가 중복일 뿐이다 |

### C-5. `RecommendErrorCode`의 나머지 6개 상수도 던지는 곳이 없음

`RecommendErrorCode.java:12-17`의 `USER_PROFILE_NOT_FOUND`·`PRODUCT_NOT_FOUND`·`INVALID_TOP_K`·`RECOMMENDATION_FAILED`·`VECTOR_SEARCH_FAILED`·`INVALID_VECTOR_DATA`는 저장소 어디에서도 참조되지 않는다(`grep -rn "RecommendErrorCode\."`는 B-1의 디코더 한 곳뿐). 개인화·유사 상품 추천 단위 소관이라 이 라운드의 B에 넣지 않았다 — [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) B-1이 이 6개 상수를 Dead Code로 다룬다. 사용자가 원하면 B-1(이 문서)과 같은 변경에서 enum 파일째 정리할 수 있다(STOP §4).

### C-6. `recipe-recommend.yml`이 prod 프로필에서만 로드됨

제외 카테고리 목록은 `application-prod.yml:10-14`의 `spring.config.import`로만 들어오고 `application.yml:7-11`에는 import가 없다. prod가 아닌 프로필로 기동하면 `RecommendProperties.excludeRecipeCategoryCodes`가 빈 목록이 되어 과일이 걸러지지 않는다. 지금은 로컬도 prod 프로필을 쓰라고 안내하므로(`IMPLEMENTATION.md:89`) 실효 영향은 없다. **왜 지금 안 하나**: 로컬 프로필을 어떻게 둘지(판단 항목 2)와 함께 정하는 게 맞다. A-2로 같은 yml을 만질 때 import를 `application.yml`로 올리는 1줄 이동을 같이 승인받을 수 있다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. 부족 재료 상품 검색을 병렬화하지 않는다 (H9)

`RecipeRecommendService.java:106-114`는 부족 핵심 재료마다 `searchProductsByIngredientName`을 순서대로 부른다. 횟수 상한은 구조적으로 작다 — 부족 핵심 재료는 핵심 재료(프롬프트 `:102`에서 3~5개)의 부분집합이라 최대 5회이고, 각 호출은 같은 ES 클러스터에 `size=2` 키워드 검색 1건이다. 이 구간이 느리다는 측정이 없고, 같은 경로의 LLM 호출(호출당 30초 상한)에 비하면 자릿수가 다르다. 병렬화하려면 실행기·예외 전파·`AsyncConfig`와의 결합이 붙는데, 그 복잡도를 정당화할 근거가 없다(`baro-ai-performance-refactor.md` "느려 보인다는 근거가 아니다"). 요청 경로 지연이 문제가 되면 먼저 판단 항목 1(LLM 예산)을 본다.

### D-2. `recommendFromCartWithMissing`의 광범위 `catch (Exception)` 유지 (H11)

`RecipeRecommendService.java:43-50`은 어떤 예외든 잡아 200 + 빈 응답으로 바꾼다. 이건 스펙 `비즈니스 규칙` "단계별 실패" 행이 명시한 fail-safe 설계이고, 예외를 종류별로 나눠도 그 결과를 쓸 소비자가 없다 — 응답은 항상 200이고 `RecommendErrorCode`는 던지지 않는다(B-1·C-5). [`cross-cutting/001`](../cross-cutting/001-llm-call-infra.md) D-2와 같은 이유다. 로그 문구가 범위를 잘못 말하는 것만 B-4에서 고친다.

### D-3. `@Qualifier`를 쓰는 수동 생성자 유지 (H12)

`ProductEmbeddingService.java:17`·`UserProfileEmbeddingService.java:44`는 이 단위 밖(임베딩)이지만 가설이 넘어와 확인만 한다. `spring-boot-java-design.md:58`이 이미 예외로 적어 둔 경우다 — 생성자 파라미터에만 걸려야 하는 스프링 애너테이션은 수동 생성자를 유지한다. 실제로 Lombok 1.18.46의 기본 복사 대상 목록(`HandlerUtil`)에는 `NonNull` 계열만 있고 `@Qualifier`는 없으며, 저장소에 `lombok.copyableAnnotations`를 설정할 `lombok.config`도 없다. 필드에 `@Qualifier`를 붙이고 `@RequiredArgsConstructor`로 바꾸면 한정자가 생성자로 복사되지 않아 빈 선택이 깨진다. 지금 구조가 맞다.

### D-4. `ResponseDto`를 벗기는 커스텀 Feign `Decoder`를 만들지 않는다 (A-1 대안 b)

Feign 클라이언트가 하나, 메서드가 하나다. 범용 언래핑 디코더는 클래스 1개 + 설정 1개를 더하고, 제네릭 타입 정보를 `Type`으로 다시 풀어야 해서 반환 타입을 `ResponseDto<CartInfo>`로 적고 `.data()`를 꺼내는 것보다 읽기 어렵다. 두 번째 Feign 클라이언트가 생겨 같은 4줄이 반복될 때 다시 본다(YAGNI).

## 백엔드 아키텍처 개선 제안

- **Resilience (Feign 타임아웃)** — **Now**. B-5와 동일하므로 반복하지 않는다.
- **Resilience (Circuit Breaker)** — **Later**. baro-shopping 장애 시 빠르게 실패할 회로 차단은 호출량 근거가 있어야 한다. 요청당 1회 호출이고 초당 요청 수 추정치가 없다([`cross-cutting/001`](../cross-cutting/001-llm-call-infra.md) 판단 항목 5와 같은 [미정]). B-5로 상한을 두면 지금 규모에서는 충분하다.
- **Async Processing (장바구니 이벤트로 추천 선계산)** — **Later**. [`decisions/002`](../../decisions/002-ingredient-name-at-index-time.md) "고려한 대안" 표가 이미 "남은 LLM 1회가 문제가 되면 `CartEventConsumer`를 활용해 재검토"로 미뤄 두었다. 판단 항목 1의 측정 결과가 "정상 케이스도 게이트웨이 5초를 넘긴다"로 나오면 그때 재검토 조건이 충족된 것이다.
- **Caching** — **Never**(이 경로). 입력이 사용자별 장바구니 상태라 키가 요청마다 다르고, [`decisions/001`](../../decisions/001-autocomplete-caching.md)이 추천 경로 캐싱을 하지 않기로 한 결정과도 맞다.

## 판단이 서지 않는 항목

1. **게이트웨이 응답 타임아웃 5초 vs baro-ai 최악 약 93초 (H8)** — `baro-gateway/.../application-prod.yml:28-30`(`response-timeout: 5s`)은 baro-ai 쓰기 범위 밖이다. 두 갈래가 있다. (a) 게이트웨이의 `ai-service-route`에만 더 긴 응답 타임아웃을 주기(게이트웨이 담당자). (b) baro-ai 쪽 예산을 줄이기 — 재시도 3회 → 2회, 호출당 30초 → 10초 등(`LlmClientConfig`, cross-cutting 소관). 어느 쪽이든 **먼저 재야 할 것**은 정상 케이스의 LLM 지연이다 — 후보 3개 + 조리법을 만드는 호출이 5초를 넘기면 실패가 아니라 정상 요청도 게이트웨이에서 끊긴다. 측정 수단은 이미 있다(`/actuator/metrics/gen_ai.client.operation.duration`, cross-cutting B-1). 덧붙여 게이트웨이의 default `Retry` 필터(`:56-65`, GET·502/503/504·2회)가 타임아웃에도 적용된다면 한 번의 클릭이 baro-ai에 LLM 호출을 최대 3번 일으킬 수 있다 — 필터의 기본 `exceptions` 목록에 타임아웃이 포함되는지는 이번에 jar로 확인하지 못했다.
2. **`http://baro-shopping:8080`이 baro-ai Pod에서 그대로 풀리는지** — 두 Service가 같은 네임스페이스면 짧은 이름으로 충분하고, 다르면 FQDN(`baro-shopping.<ns>.svc.cluster.local`)이 필요하다. 매니페스트에 네임스페이스 지정이 없어 배포 담당자 확인이 필요하다 `[미정]`. 같은 김에 전달할 관찰: `baro-order`는 `feign.client.config.*.url`(3.x 시절 키)을 쓰는데 이 저장소의 OpenFeign 4.3.0은 `spring.cloud.openfeign.client.*`로 바인딩한다 — baro-order도 같은 증상일 수 있으나 그 모듈 소유자 판단이다. 로컬 프로필(`application-local.yml` 부재, 기본 `local` 프로필이 실제로는 안 쓰임)을 어떻게 정리할지도 사용자 결정 `[미정]`.
3. **테스트용 엔드포인트를 지울지, 프로필로 숨길지 (B-2)** — baro-shopping 없이 LLM 경로를 손으로 확인할 손잡이가 필요한지에 달렸다. A-1·A-2 반영 후에는 실제 경로로 확인할 수 있어 삭제(a)를 권하지만, 확인 절차의 편의는 사용자가 안다.
4. **`/recipes/{userId}`가 경로 변수로 사용자를 신뢰하는 것** — `SearchController.java:36`은 `X-User-Id` 헤더를 읽는데 `RecommendationController`는 개인화·레시피 모두 경로 변수를 쓴다. 게이트웨이에 인증 필터가 없다는 전제([`platform.md`](../../product/platform.md), `glossary.md` "`X-User-Id`")에서는 누구나 남의 장바구니 재료 목록을 조회할 수 있다. 헤더로 바꾸면 API 계약이 바뀌고 게이트웨이 설계와 맞물리므로 여기서 등급을 매기지 않는다 — 사용자·게이트웨이 담당자 확인 필요.
5. **B-5 타임아웃 값** — baro-shopping의 정상 장바구니 응답 시간(항목별 상품·재고 재조회 포함)을 로컬에서 몇 번 재 봐야 안전한 값을 정할 수 있다 `[미정]`.
6. **C-2의 제외 판정 근거를 장바구니 스냅샷에서 ES 문서로 옮길지** — 정책은 같지만 값의 출처가 바뀐다. shopping 쪽 조회 실패가 실제로 얼마나 나는지 모른다 `[미정]`.
7. **B-3의 record 전환이 스키마 생성기에서 그대로 되는지** — `BeanOutputConverter`가 쓰는 victools 생성기의 record 지원은 실호출 1회로만 확정할 수 있다. 안 되면 불변 클래스로 대체하며, 항목의 목적(응답 타입 분리)은 그대로다.

## 승인 결과 (2026-09-05)

사용자가 A·B 전 항목을 승인했다. 채점식 단순화([`recommend/002`](../recommend/002-recipe-scoring.md) C-1)는 이 단위가 구현 순서상 마지막이라 **착수 시점에 결정**하기로 했다. 판단 항목 1·2·4는 팀 전달, 나머지는 구현 세션에서 결정한다.

원래 승인 대기 문구: 사용자 승인 후 A/B 항목만 우선순위 순으로 구현한다 — 제안 순서는 **A-2 → A-1**(둘 다 High, A-2가 요청 경로에서 먼저 실패하고 yml만 만진다) → B-5(같은 yml 블록) → B-1 → B-4 → B-2 → B-3. C/D는 이번 라운드에서 수정하지 않는다. A-1·A-2·B-5의 검증은 baro-shopping을 로컬에 띄워야 하므로, 구현 후 사용자가 직접 실행할 수동 절차로 `refactor-log.md`에 남긴다.

### 구현 결과 (2026-09-05)

A 2건·B 5건을 전부 반영했다. 무엇을 바꿨고 무엇을 남겼는지, 검증 결과는 [`refactor-log.md`](refactor-log.md) 2026-09-05 항목에 있다. 착수 시점에 정하기로 한 것과 구현 세션에서 정해진 것은 아래와 같다.

| 항목 | 결정 | 반영 위치 |
|------|------|-----------|
| 채점식([`recommend/002`](../recommend/002-recipe-scoring.md) C-1) | **삭제한다**(사용자 결정). 점수·판매 기회 보너스·동점 셔플을 걷어내고 LLM이 낸 순서의 첫 번째 유효 후보를 쓴다. `safe-refactor` 절대 원칙 2(정책 불변)를 사용자 승인으로 벗어난 유일한 변경이다 | `RecipeCandidates.chooseFirst` · 스펙 비즈니스 규칙 |
| 3 (테스트 엔드포인트) | **삭제**(a안, 사용자 결정) | `RecommendationController` · `FEATURES.md` · `README.md` |
| 5 (B-5 타임아웃 값) | **연결 1초 · 읽기 3초**(감사 잠정 제안값, 사용자 결정). baro-shopping 정상 응답 시간은 재지 못했다 — 담은 상품이 많을 때 정상 조회가 3초를 넘길 위험이 남는다 | `application-prod.yml` |
| 7 (record 스키마 생성) | **해소** — `spring-ai-model` 1.1.8이 쓰는 `jsonschema-generator` 4.38.0으로 같은 구조의 record를 만들어 스키마 생성과 JSON→record 변환을 로컬에서 실행해 확인했다(`spring-reviewer` 2026-09-05). 다만 실제 Gemini 응답으로의 end-to-end 확인은 아직이다 | `RecipeCandidateDraft` |
| 1·2·4·6 | **미해결** — 게이트웨이·배포 담당자 확인 또는 운영 재개 후 측정이 필요하다 | 스펙 리스크 표 |

**채점식 삭제의 되돌릴 수 없는 성격**: 삭제 전에도 후보별 점수를 로그로 남기지 않아, 삭제 전후의 추천 품질을 비교할 자료가 없다. 되돌리려면 [`recommend/002`](../recommend/002-recipe-scoring.md) C-1에 적힌 가중치(핵심 ×10, 추가 ×3, 부족 0개 −10 / 1~2개 +15 / 3개 이상 −5)를 근거로 복원해야 한다.

**B-1의 부수 효과**: `RecommendErrorCode.CART_NOT_FOUND`를 지우자 남은 상수가 0개가 됐다(나머지 6개는 [`vector-similarity/001`](../vector-similarity/001-vector-search-and-diversity.md) B-1에서 이미 삭제). 상수 없는 enum을 남기지 않고 파일째 지웠다.
