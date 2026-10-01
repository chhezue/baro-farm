# Specs — 기능 설계

`specify` 스킬이 만드는 기능 스펙을 도메인 폴더로 나눠 둔다. 이 폴더가 `{{스펙 저장소}}` 슬롯의 기본값이다.

- 파일명: kebab-case, 한 파일 = 한 기능 또는 한 리팩터 단위
- 템플릿: [`.claude/skills/specify/references/spec-template.md`](../../../.claude/skills/specify/references/spec-template.md)
- 상태: `Draft` → `Approved` → `Implemented`

## 도메인 폴더 ↔ 코드 패키지

두 도메인에 걸치는 스펙은 **주로 바뀌는 상태가 속한 도메인**에 둔다.

| 폴더 | 코드 패키지 | 담당 | 비고 |
|------|-------------|------|------|
| `search/` | `com.barofarm.ai.search` | 작성자 소유 | 키워드 검색·자동완성·상품 인덱싱 |
| `recommend/` | `com.barofarm.ai.recommend` | 작성자 소유 | 개인화·유사상품·레시피 추천, 메도이드 다양성 |
| `embedding/` | `com.barofarm.ai.embedding` | 작성자 소유 | 상품/사용자 프로필 임베딩 |
| `event-log/` | `com.barofarm.ai.event`, `com.barofarm.ai.log` | 작성자 소유 | Kafka consumer(cart/order/product) + 로그 저장 파이프라인 |
| `chat/` | `com.barofarm.ai.chat` | 작성자 소유 | 미착수(컨트롤러·서비스 스텁만 존재) |
| `review/` | `com.barofarm.ai.review` | 팀원 소유 | 감정분석·베스트리뷰·요약 — 이 저장소 스펙은 아직 없음(로드맵은 `../REVIEW_ANALYSIS_PHASES.md` 참고, 필요 시 팀원이 채움) |
| `cross-cutting/` | (도메인 무관) | — | |
