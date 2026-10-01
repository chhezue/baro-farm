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
| 다른 모듈 → baro-ai | HTTP(REST, `X-User-Id` 헤더로 사용자 식별, 별도 인증 토큰 검증은 하지 않음 — 게이트웨이가 이미 처리했다고 신뢰) | `SearchController`, `RecommendationController` |
| 다른 모듈 → baro-ai | Kafka 이벤트 소비 | `cart-events`·`order-events`·`product-events`·`review-events` |
| baro-ai → 다른 모듈 | Feign | `baro-shopping`의 장바구니 조회(`CartClient`) |
| baro-ai → 다른 모듈 | Kafka 이벤트 발행 | `review-summary` 이벤트(review 도메인) |

## 인증

baro-ai 자체에는 인증 로직이 없다. `X-User-Id` 헤더가 있으면 그 값을 신뢰하고(로그 저장·개인화용), 없으면 비로그인 요청으로 처리한다. 토큰 검증·인가는 게이트웨이 책임 — baro-ai 코드에 `@Aspect`·권한 어노테이션이 없는 이유이기도 하다(`harness-map.md` 스택 옵션 "권한·활동 어노테이션" = 꺼짐).

## 관련 문서

- [`docs/product/mvp.md`](mvp.md)
- [`.claude/rules/harness-map.md`](../../../.claude/rules/harness-map.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 코드(컨트롤러 헤더·Feign·Kafka 토픽) 기준으로 작성 |
