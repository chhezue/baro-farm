# 003 — LLM 제공자를 Gemini로 전환

> 상태: 확정 (2026-09-04)

baro-ai가 쓰는 LLM 제공자와, 그 전제가 되는 Spring AI 버전을 정한 결정이다.

## 맥락

OpenAI는 사용량만큼 비용을 내야 하고, Gemini는 무료 티어가 있다. 팀이 아직 수익이 없는 dev 단계라 **운영 비용을 0으로 만드는 것**이 이번 전환의 목적이다.

전환을 검토하면서 두 가지를 먼저 확인해야 했다.

**① 챗과 임베딩을 같은 제공자로 묶어야 하는가?** 아니다. Spring AI는 챗 모델과 임베딩 모델을 각각 다른 제공자로 띄울 수 있다. 그래서 "임베딩 때문에 OpenAI를 유지해야 한다"는 제약은 애초에 없었다. 다만 **목표가 비용 0이라면** 임베딩만 OpenAI로 남겨도 유료 키를 계속 유지해야 하므로 목적을 달성하지 못한다. 그래서 둘 다 옮긴다.

**② 지금 버전에서 무료 Gemini를 쓸 수 있는가?** 쓸 수 없었다. Gemini에는 두 갈래가 있는데 서로 다르다.

| | Google AI Studio | Vertex AI |
|---|---|---|
| 인증 | API 키 한 줄 | GCP 프로젝트 + 서비스 계정 |
| 요금 | **무료 티어 있음** | 유료 (무료 크레딧은 별개) |
| Spring AI 모듈 | `spring-ai-starter-model-google-genai` | `spring-ai-starter-model-vertex-ai-gemini` |

baro-ai가 쓰던 Spring AI **1.0.0-M5**의 BOM에는 Vertex AI 쪽만 있었다(Maven Central의 BOM pom을 직접 확인). 즉 무료 티어로 가려면 Spring AI 업그레이드가 선행 조건이었다.

## 결정

- **Spring AI를 `1.0.0-M5`에서 `1.1.8`로 올린다.**
- **챗·임베딩 모두 Google AI Studio Gemini로 전환한다.** OpenAI 의존성은 제거한다.
- 임베딩 출력 차원을 **1536으로 고정**해 기존 Elasticsearch 매핑(`dims = 1536`)을 유지한다.

## 근거

- **Spring Boot 업그레이드가 불필요하다는 것을 실물로 확인했다.** `spring-ai-starter-model-google-genai`는 `1.1.0`부터 존재하고, `1.1.8`의 pom은 **Spring Boot 3.5.15** 기준으로 빌드돼 있다. 저장소 루트가 Spring Boot **3.5.8**이므로 같은 3.5.x 라인이다.
  - Spring AI **2.0.x는 Spring Boot 4.1을 요구**한다(2.0.1 pom에서 확인). 2.0으로 갔다면 5인이 함께 쓰는 모노레포 전체의 Boot 4 업그레이드가 필요했다. **1.1.8은 `baro-ai/build.gradle` 하나만 바꾸면 되고 다른 모듈에 영향이 없다.**
- **마일스톤 릴리스를 벗어난다.** `1.0.0-M5`는 정식 릴리스가 아니다. 무료 Gemini와 무관하게 올릴 이유가 있었다.
- **임베딩 재색인 부담이 없다.** 서로 다른 모델의 벡터는 비교할 수 없어 부분 전환이 불가능한데, 운영 서버가 내려가 있어 보존할 데이터가 없다(`AGENTS.md` 운영 상태, `.claude/rules/baro-ai-performance-refactor.md` 데이터 마이그레이션 방침).
- **차원을 1536으로 고정한 이유**: Gemini 임베딩 모델은 출력 차원을 조정할 수 있다(`GoogleGenAiTextEmbeddingOptions.dimensions`). 이를 1536으로 두면 `ProductDocument`·`UserProfileEmbeddingDocument`의 ES 매핑과 코사인 유사도·메도이드 계산 코드를 그대로 쓸 수 있다. 벡터 자체는 어차피 재생성해야 하므로, 바꾸지 않아도 되는 것까지 함께 바꿀 이유가 없다.

## 확인한 실물 값

빈 이름과 설정 키는 추측하지 않고 `spring-ai-autoconfigure-model-google-genai:1.1.8` 실물을 열어 확인했다.

| 항목 | 값 |
|------|-----|
| 챗 모델 빈 | `googleGenAiChatModel` |
| 임베딩 모델 빈 | `googleGenAiTextEmbedding` (`...EmbeddingModel`이 **아님**) |
| 연결 설정 | `spring.ai.google.genai.api-key` |
| 챗 옵션 | `spring.ai.google.genai.chat.options.*` |
| 임베딩 연결 | `spring.ai.google.genai.embedding.api-key` (챗과 별도 속성) |
| 임베딩 옵션 | `spring.ai.google.genai.embedding.text.options.*` |

## 트레이드오프

무료 티어를 얻는 대가로 감수하는 것들이다.

| 항목 | 내용 |
|------|------|
| 호출 한도 | 분당·일일 요청 수 제한이 있다. 레시피 추천의 LLM 호출을 3회에서 1회로 줄인 [`002`](002-ingredient-name-at-index-time.md)가 선행돼 있어 여유가 생겼지만, 한도 자체는 남아 있다 |
| 데이터 사용 | 무료 티어에 보낸 데이터는 모델 개선에 쓰일 수 있다. **지금은 dev이고 실사용자 데이터가 없어 감수한다.** 실사용자의 장바구니·리뷰가 들어가는 시점에는 유료 티어로 올려야 하고, 그러면 "무료라서"라는 이 결정의 전제가 사라지므로 재검토한다 |
| SLA | 없다. 데모 중 장애가 나도 대응 수단이 없다 |

## 영향

- **`review` 도메인 동작이 함께 바뀐다.** `ReviewSummaryService`는 공용 `ChatClient`를 주입받으므로, 코드를 한 줄도 고치지 않았지만 요약을 만드는 모델이 OpenAI에서 Gemini로 바뀐다. `review`는 성능 리팩토링 범위 밖(팀원 소유)이라 **코드는 건드리지 않았고**, 이 사실만 기록해 둔다.
- 환경 변수가 바뀐다: `BARO_AI_OPENAI_KEY`는 `BARO_AI_GEMINI_KEY`로, 모델명 오버라이드용 `SPRING_AI_OPENAI_MODEL`·`SPRING_AI_OPENAI_EMBEDDING_MODEL`은 `SPRING_AI_GEMINI_MODEL`·`SPRING_AI_GEMINI_EMBEDDING_MODEL`로 바뀐다. `k8s/apps/baro-ai/deployment.yml`의 키 이름은 함께 고쳤고, **클러스터의 `baro-ai-secrets` 시크릿에 `BARO_AI_GEMINI_KEY` 항목을 추가하는 것은 배포 담당자가 해야 한다**.
- API 응답 계약은 변경 없음.

## 고려한 대안

| 대안 | 기각 이유 |
|------|-----------|
| 챗만 Gemini, 임베딩은 OpenAI 유지 | 유료 OpenAI 키를 계속 유지해야 해서 "비용 0"이라는 목적을 달성하지 못한다. 임베딩 비용 자체는 이미 매우 작아 남길 실익도 없다 |
| Spring AI 2.0.x로 올리기 | Spring Boot 4.1을 요구한다. 5인 공용 모노레포 전체에 영향을 주는 결정이라 baro-ai 혼자 정할 수 없다 |
| M5에 머물고 Vertex AI Gemini 사용 | 무료가 아니고, GCP 프로젝트·서비스 계정 세팅이 필요해 목적과 수단이 모두 어긋난다 |
| OpenAI 유지 | 목적이 비용 0이라 성립하지 않는다. 다만 무료 티어의 한도·데이터 사용 조건이 문제가 되면 되돌릴 수 있다 — 되돌리는 비용은 `build.gradle`·설정·`@Qualifier` 3곳과 재색인이다 |
