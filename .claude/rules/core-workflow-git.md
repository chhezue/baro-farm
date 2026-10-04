# Core — Git 컨벤션 (이 저장소 실측 기준)

브랜치·게이트·트랙 사이클은 [`core-workflow.md`](core-workflow.md) — 여기는 브랜치명·커밋 메시지·PR·이슈 컨벤션만 다룬다.

저장소 루트에 `CONTRIBUTING.md`가 없어 **이 절이 SSOT다.** 값은 추정이 아니라 `baro-farm-be`의 실제 커밋·브랜치 이력에서 확인했다(2026-09-04). 팀이 관례를 바꾸면 이 절도 같은 턴에 고친다.

## 브랜치

`main`에서 분기하고, 이슈 번호를 반드시 넣는다.

```
feature/issue-{번호}-{영문-설명}
```

실제 예: `feature/issue-116-refactor-order-payment`, `feature/issue-113-modify-readme`. 기능 외 작업도 대부분 `feature/`를 쓰고 있어 접두사를 새로 늘리지 않는다.

## 커밋 메시지

```
[Type] #{이슈번호} - {한글 설명}
```

실제 예: `[Feat] #116 - 결제 멱등성 보장 로직 추가`. 이슈 번호가 없는 작업은 `[Docs] 하네스 설계 배경·회고 문서 추가`처럼 `#n`을 생략한다.

| Type | 쓰임 | 실측 건수 |
|------|------|-----------|
| `[Fix]` | 버그 수정 | 368 |
| `[Feat]` | 기능 추가 | 279 |
| `[Chore]` | 설정·빌드·정리 | 226 |
| `[Refactor]` | 동작 변경 없는 개선 | 90 |
| `[Docs]` | 문서만 변경 | 19 |

대괄호 표기와 대문자 시작을 지킨다 — `[fix]`·`[FIX]`처럼 흐트러진 사례가 소수 있지만 따라 하지 않는다. `[Test]`·`[Hotfix]`도 쓰인 적 있으나 드물어 위 5개를 기본으로 본다.

## 커밋 날짜 지정 (2026-09-04 사용자 결정)

사용자가 "특정 날짜로 커밋해줘"라고 요청하면 **GitHub에 찍히는 committer date·author date만** 바꾼다.

- **바꾸지 않는 것:** 스펙·감사·결정문 등 `.md` 파일 본문에 적힌 승인일·확정일·변경 이력 날짜. "커밋 날짜를 바꿔달라"는 요청이 문서 내용의 날짜까지 바꾸라는 뜻은 아니다.
- **바꾸는 것:** `git commit`의 author date와 committer date 둘 다. 하나만 바꾸면(`--date`만 쓰면 committer date는 실행 시점 그대로 남는다) GitHub에 "커밋함" 시각과 "저자가 작성한" 시각이 어긋나 보이므로, 항상 `GIT_AUTHOR_DATE`·`GIT_COMMITTER_DATE` 둘 다 지정한다:

  ```bash
  GIT_AUTHOR_DATE="2026-10-04T12:00:00+09:00" GIT_COMMITTER_DATE="2026-10-04T12:00:00+09:00" \
    git commit -m "..."
  ```

- **연도 해석 — 사용자가 못박은 규칙:** 사용자가 요청한 날짜가 **오늘(세션 진행일) 기준 미래**라면, 그 해는 **2025년이 아니라 무조건 2026년**이다. "10/4"처럼 연도 없이 날짜만 말하면 이 규칙으로 연도를 채운다.

## Pull Request

PR을 만들 때 지키는 규칙이다.

| 항목 | 규칙 |
|------|------|
| base | `main` |
| 본문 | [`.github/PULL_REQUEST_TEMPLATE.md`](../../.github/PULL_REQUEST_TEMPLATE.md) 양식 — 관련 이슈(`closes #n`)·작업 내용·변경된 모듈 체크·체크리스트 |
| merge | **Create a merge commit** (Squash 금지) — `Merge pull request #117 from dogs-team/...` 형태로 이력이 남는다 |
| merge 후 | 작업 브랜치 삭제 (원격 + 로컬). merge 안 된 브랜치는 삭제 금지 |

## 이슈

[`.github/ISSUE_TEMPLATE/`](../../.github/ISSUE_TEMPLATE/)의 두 양식이 제목 접두사를 강제한다 — `task.md`는 `[Chore] `, `bug_report.md`는 `[Fix] `. 새 이슈를 만들 때 이 양식을 벗어나지 않는다.

**⚠️ 이슈·브랜치·PR 생성은 항상 먼저 확인** — [`core-workflow.md`](core-workflow.md) G2 절의 규칙이 형식보다 우선한다.
