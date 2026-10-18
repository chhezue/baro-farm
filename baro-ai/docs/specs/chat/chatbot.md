# 챗봇

> 상태: Draft (미착수)
> MVP: TBD
> 관련 BR: N/A

## 목표

[미정] — 실제 목적(범용 Q&A, 레시피 상담, 고객 지원 등)이 아직 정해지지 않았다. 자리만 잡고 있던 빈 클래스 4개는 2026-09-05에 지웠고, 이 문서가 백로그 기록을 대신한다.

## 배경

- 코드: **없다.** 엔드포인트 0개·로직 0줄이던 스텁 4개(`ChatbotController`·`ChatService`·`ChatRequest`·`ChatResponse`)를 2026-09-05에 삭제했다 — 빈 컨트롤러가 Swagger에 경로 접두사만 만들고 있었다. 목적이 정해지면 이 문서를 근거로 새로 만든다
- 참고할 수 있는 기존 LLM 연동 사례: `review.application.summary.ReviewSummaryService`(`ChatClient` 사용), `recommend.infrastructure.llm.RecipeCandidateGenerator`

## 요구사항

### Must Have

- [ ] [미정 — 목적부터 확정 필요]

### Out of Scope (이번 스펙에서 하지 않음)

- [미정]

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| | (없음 — `com.barofarm.ai.chat` 패키지 자체가 2026-09-05에 삭제됐다) | | |

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 챗봇의 목적·사용처 | [미정] | 구현 착수 전 반드시 사용자에게 확인 (`core-guardrails.md` ⛔ §1.4) |
| 기존 도메인과의 관계 | [미정] | 레시피 추천·리뷰 요약과 별개 기능인지, 이들을 통합하는 대화형 인터페이스인지 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 — 미착수 상태를 스펙으로 명시화(백로그 기록 목적) |
