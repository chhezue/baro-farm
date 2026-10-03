# baro-ai — 현재 이니셔티브: 근거 기반 성능 리팩토링 (2026-09-04~)

이 저장소(`baro-ai`) 고유의 **지금 진행 중인 작업 방향**을 담는 always-load 규칙이다. `harness-map.md`(슬롯)·스택 옵션과 달리 이 파일은 **한시적**이다 — 이니셔티브가 끝나거나 방향이 바뀌면 이 파일을 갱신하거나 지운다(`core-workflow.md` G4 "문서 갱신 점검"에서 매번 재확인).

## 무엇을 하는 중인가

**기능 추가가 아니라 전면 리팩토링이다.** 다만 `safe-refactor`가 원래 상정하는 "도메인 1개씩 좁게" 감사가 아니라, **아래 범위 전체**를 대상으로 성능·전문성 위주 개선을 진행한다. 캐싱, 프롬프트 튜닝(LLM 호출 비용·지연·품질), 비동기 처리 같은 **백엔드 성능·설계 품질** 축이 중심이다.

**목표는 성능만이 아니라 "백엔드 로직의 전문성"이다** — 더 견고하고 정확한 설계로 바꾸는 것도 이 이니셔티브에 포함된다. 단, **오버엔지니어링은 금지** — 주니어 수준보다는 위여야 하지만, 이 프로젝트 규모에 안 맞는 과도한 추상화·패턴 남용은 `safe-refactor`의 YAGNI 원칙 위반이다. "시니어스럽게 보이려고" 넣는 복잡도는 오히려 감점 요인.

**트랙: 전부 B(감사·리팩터)다.** `core-workflow.md`의 Track A(기능·`specify`)가 아니라 Track B(`safe-refactor`)를 쓴다. 단 `safe-refactor`의 절대 원칙 중 **API Contract 동결 원칙은 baro-ai에서 해제**한다 — 아래 "API 계약 변경 방침" 참고. 비즈니스 로직·정책 불변, 성능 악화 금지, YAGNI는 그대로 적용된다.

## API 계약 변경 방침 (2026-09-04 사용자 결정 — safe-refactor 원 원칙에서 벗어남)

이 프로젝트는 **아직 릴리즈되지 않았고 실제 서버도 내려간 상태**라 하위 호환을 지킬 대상(기존 클라이언트·트래픽)이 없다. 그래서 API 계약(요청/응답 필드, 엔드포인트, ErrorCode)이 **더 정확하고 명확해지는 방향이면 바꿔도 된다** — `safe-refactor` 원 스킬의 "API Contract 100% 동일" 원칙을 그대로 따르지 않는다.

- 여전히 지킬 것: **비즈니스 로직·정책은 그대로**(계약 표현 방식이 바뀌는 것과 로직이 바뀌는 것은 다르다), `core-guardrails.md` STOP §2(ErrorCode 동시갱신)는 계약이 바뀔 때도 그대로 적용
- 실제로 다른 모듈(팀원 담당)이나 프론트가 호출하는 엔드포인트를 바꾸면, 막지는 않되 **refactor-log.md에 Before/After를 분명히 남긴다** — 5인 공용 저장소라 나중에 팀원이 놀라지 않게
- OpenAPI 계약 보호 스택 옵션은 이미 꺼져 있으므로(`harness-map.md`) `oasdiff` 검증은 애초에 안 쓴다

### 데이터 마이그레이션 방침 (dev 모드 전제)

같은 이유로 **저장 스키마도 동결 대상이 아니다.** 운영 서버가 내려가 있어 보존할 실데이터가 없으므로, ES 매핑 변경·인덱스 재생성·전체 재색인이 필요한 개선안을 "마이그레이션 비용" 때문에 기각하지 않는다. 임베딩 모델·차원 교체(`ProductDocument.vector`, `UserProfileEmbeddingDocument` 모두 현재 `dims = 1536`)도 여기 포함된다. 단 실행은 사용자 승인 후.

## 범위

| 도메인 | 코드 패키지 | 상태 |
|--------|-------------|------|
| search | `com.barofarm.ai.search` | 대상 |
| recommend | `com.barofarm.ai.recommend` | 대상 |
| embedding | `com.barofarm.ai.embedding` | 대상 |
| event-log | `com.barofarm.ai.event`, `com.barofarm.ai.log` | 대상 |
| chat | `com.barofarm.ai.chat` | 대상 (단, 아직 미구현 — [`docs/specs/chat/chatbot.md`](../../baro-ai/docs/specs/chat/chatbot.md) 참고. 성능 리팩토링 대상 코드 자체가 없음) |
| 공유 인프라 config | `com.barofarm.ai.config`(`AsyncConfig`·`KafkaConsumerConfig`·`KafkaProducerConfig`·`ElasticsearchConfig`·`LlmClientConfig`·`CacheConfig`) | 대상 — 단 위 5개 도메인에 영향이 있을 때만. **review 전용 설정**(`NegativeReviewPropertiesConfig`·`SentimentPropertiesConfig`)은 제외 |
| review | `com.barofarm.ai.review` | **범위 밖 — 코드 변경 안 함.** 팀원 소유 도메인 |

**review 패키지를 건드리게 되는 상황이 생기면** (예: 공유 이벤트 모델 변경, 공통 config 개편) — STOP하고 사용자에게 먼저 확인한다. 하드 블로킹 훅은 없다 — 기술적 차단이 아니라 `core-guardrails.md` STOP 패턴(멈추고 질문)으로 다룬다.

## 근거 기준 (이 이니셔티브의 핵심 제약)

**"일반적으로 좋다"·"베스트 프랙티스다"·"보통 이렇게 한다"는 근거로 변경하지 않는다.** `audit-checklist.md` 15번 항목이 이미 "최신 기술이라서"류 근거를 금지하고 있었는데, 이 이니셔티브에서는 그보다 더 엄격하게 **적극적 증거**를 요구한다 — 지난 세션에서 실제로 문서가 미구현 Redis 캐시를 사실처럼 서술했던 사고([`docs/specs/search/product-search-and-autocomplete.md`](../../baro-ai/docs/specs/search/product-search-and-autocomplete.md) 리스크 항목)가 계기다.

**A(반드시 수정) 항목, 특히 Category가 Performance인 항목은 감사 문서에 다음을 추가로 명시해야 한다** (감사 템플릿 A 항목 포맷의 "근거" 필드 — [`docs/templates/how-to-doc.md`](../../baro-ai/docs/templates/how-to-doc.md)):

1. **현재 동작의 증거** — 코드로 확인 가능한 사실(예: "`@EnableCaching`이 없어 `@Cacheable`이 no-op임을 grep으로 확인"), 또는 재현 가능한 관찰(간단한 타이밍 로그·반복 호출 스크립트 등). "느릴 것 같다"·"비효율적으로 보인다"는 증거가 아니다.
2. **개선 후 검증 방법** — 어떻게 효과를 확인할 것인지(테스트로 캐시 hit 확인, 호출 횟수 감소 확인, LLM 토큰 수 비교 등). 프로덕션 서버가 내려가 있어 실측 APM은 못 쓰므로, **로컬에서 재현 가능한 방법**(단위/통합 테스트, 반복 호출 스크립트, 토큰 카운트 비교)이어야 한다.
3. C/D(안 하는 게 낫다) 판단도 마찬가지로 "왜 지금 필요 없는지"를 추측이 아니라 실제 트래픽·데이터 규모(예: 상품 수, 초당 요청 수 추정치)에 근거해 적는다 — 모르면 `[미정]`으로 남기고 사용자에게 묻는다.

**LLM/프롬프트 튜닝은 `audit-checklist.md` 16번 항목**(이 이니셔티브에서 추가)을 따른다 — 토큰 수·모델 선택·응답 파싱 안정성·재시도·배치화 가능성.

## 완료 기준 (도메인별)

`safe-refactor` 6단계(Audit → 승인 → Implement → Verify → Report → 다음 도메인) 그대로. 단 이 이니셔티브는:

- **OpenAPI 계약 보호가 꺼진 프로젝트라 `oasdiff` 생략** — 대신 `{{테스트 명령}}` 전체 통과 + (테스트가 없는 도메인은) 최소 수동 확인 절차를 감사 문서에 명시
- 도메인 5개를 전부 하나의 큰 라운드로 취급하지 않는다 — **여전히 한 번에 한 도메인**. 순서는 사용자에게 확인(제안 우선순위: search → embedding → recommend → event-log → chat)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 범위(search/recommend/embedding/event-log/chat, review 제외)·근거 기준 확정 |
