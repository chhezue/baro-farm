# Harness Retro — 하네스 개선 후보

`retro` 스킬이 승인 후 여기에 append 한다. **코드·설계** 개선은 여기가 아니라 `core-followup.md` 💡 후속 제안이 담당한다.

| 날짜 | 증상 (같은 실수 2회+) | 후보 조치 | 상태 |
|------|----------------------|-----------|------|
| 2026-09-05 | `openapi-conventions-operation.md`의 "`description` 대신 Javadoc + `therapi-runtime-javadoc`" 규칙이 baro-ai 실물과 다르다 — `build.gradle`에 therapi 의존성이 없어 Javadoc이 Swagger에 반영되지 않는다(cross-cutting/002 C-2). TripFit에서 물려받은 스택 전제가 검증 없이 남은 사례 | 규칙을 스택 옵션으로 표시하고 `harness-map.md`에서 baro-ai는 "꺼짐"으로 명시, 또는 therapi 도입을 사용자에게 제안 | 후보 |
| 2026-09-05 | `.claude/agents/researcher.md`가 Spring AI를 `1.0.0-M5`(마일스톤)로 전제하지만 `build.gradle`·로컬 jar는 **1.1.8 GA**다(researcher 2026-09-05 조사에서 발견). 위와 같은 "물려받은 버전 전제" 사례 2회째 | `researcher.md`의 Spring AI 행·함정 절을 1.1.8 기준으로 고치고, 버전 값은 `harness-map.md` 슬롯이나 `build.gradle` 참조로 바꿔 규칙에 숫자를 박지 않는다 | 후보 |
