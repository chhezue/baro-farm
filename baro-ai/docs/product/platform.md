# 클라이언트 전제 — baro-ai

baro-ai를 누가, 무엇으로 호출하는지의 SSOT. `harness-map.md`의 `{{클라이언트 전제}}` 슬롯이 가리키는 문서다.

## 언제 이 문서를 보는가

- 새 API를 추가하기 전에, 이 API를 누가 호출할지(사람? 다른 모듈?) 확인할 때
- 인증·헤더 전제를 확인할 때

## 결론

**baro-ai에는 외부(웹/앱) 클라이언트가 없다.** 전부 내부 MSA 호출이다.

| 호출자 | 방식 | 예 |
|--------|------|-----|
| `baro-gateway` | HTTP, 사람의 요청을 라우팅 | 검색·추천 API 전체 |
| 다른 모듈 → baro-ai | HTTP(REST). 사용자 식별은 **경로 변수**(`/personalized/{userId}`·`/recipes/{userId}`)로 받고, 별도 인증 토큰 검증은 하지 않는다 — 게이트웨이가 이미 처리했다고 신뢰 | `SearchController`(사용자 식별 없음), `RecommendationController` |
| 운영자 → baro-ai | HTTP(REST). `X-Admin-Token` 헤더를 baro-ai가 직접 대조한다 | `ProductReindexController`(재색인) |
| 다른 모듈 → baro-ai | Kafka 이벤트 소비 | `cart-events`·`order-events`·`product-events`·`review-events` |
| baro-ai → 다른 모듈 | Feign | `baro-shopping`의 장바구니 조회(`CartClient`) — 이때 `X-User-Id` 헤더를 **내보낸다** · 재색인용 상품 목록 조회(`ProductCatalogClient`) |
| baro-ai → 다른 모듈 | Kafka 이벤트 발행 | `review-summary-events` 토픽(review 도메인) · 재시도를 소진한 메시지를 옮기는 `{원래 토픽}.DLT`(2026-09-08) |

## 인증

사용자 인증은 baro-ai가 하지 않는다. 다만 **관리 API 하나는 예외로 baro-ai가 직접 인가한다** — 재색인(`ProductReindexController`)은 `X-Admin-Token` 헤더를 환경변수 `BARO_AI_ADMIN_TOKEN`과 대조해 다르면 403 `ADMIN_TOKEN_INVALID`로 끊고, 토큰이 설정돼 있지 않으면 항상 거절한다(2026-09-09). 사용자 쪽에서 `X-User-Id` 헤더를 읽는 곳은 `RecommendationController`의 개인화·레시피 API뿐이다 — 헤더가 오면 경로 변수 `{userId}`와 대조해 다르면 403 `USER_MISMATCH`, 없으면 경로 값을 그대로 믿는다(2026-09-08). 검색은 사용자를 아예 식별하지 않는다(2026-09-07에 검색 신호를 빼면서 헤더 수신도 함께 없앴다). `X-User-Id`가 등장하는 유일한 곳은 baro-ai가 `baro-shopping`을 부를 때 실어 보내는 **아웃바운드** 헤더다(`CartClient`). 사용자 토큰 검증·인가는 게이트웨이 책임 — baro-ai 코드에 `@Aspect`·권한 어노테이션이 없는 이유이기도 하다(관리 토큰 대조는 컨트롤러 안의 한 줄 검사다)(`harness-map.md` 스택 옵션 "권한·활동 어노테이션" = 꺼짐).

## 관련 문서

- [`docs/product/mvp.md`](mvp.md)
- [`.claude/rules/harness-map.md`](../../../.claude/rules/harness-map.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 코드(컨트롤러 헤더·Feign·Kafka 토픽) 기준으로 작성 |
