# 골든셋 실행기 — 레시피 추천 프롬프트 품질 측정

레시피 추천 프롬프트를 고치기 **전에** 현재 품질을 재고, 고친 **뒤** 같은 방식으로 다시 재서 비교하는 도구다. 판정 기준·시나리오의 SSOT는 [`docs/specs/cart-recipe/recipe-quality-redesign.md`](../../docs/specs/cart-recipe/recipe-quality-redesign.md)이고, 이 폴더는 그 문서를 실행 가능하게 만든 것이다.

## 왜 필요한가

이 기능은 과거에 "할루시네이션이 심해 후보를 3개로 늘렸다"·"과일이 이상한 요리를 만들어 제외했다"는 판단으로 고쳐졌는데, **그 관찰이 어디에도 남지 않아** 지금 다시 판단할 근거가 없다. 채점식을 지울 때도 같은 일이 있었다. 그래서 이번에는 재는 도구부터 만든다.

## 준비

Gemini API 키 하나만 있으면 된다. Elasticsearch·Kafka·`baro-shopping`은 **필요 없다** — 앱을 띄우지 않고 프롬프트 문자열을 Gemini에 직접 던지기 때문이다.

```bash
export BARO_AI_GEMINI_KEY="발급받은_키"
```

Python 3.10+ 표준 라이브러리만 쓴다. 설치할 패키지가 없다.

## 실행

**반드시 `before`를 먼저 돌린다.** 프롬프트를 고친 뒤에는 기준선을 만들 방법이 없다.

```bash
cd baro-ai/scripts/goldenset && python3 run_goldenset.py --arm before
```

프롬프트를 고친 뒤 같은 방식으로:

```bash
cd baro-ai/scripts/goldenset && python3 run_goldenset.py --arm after
```

한 팔당 LLM 호출 42회(시나리오 14개 × 3회, 보유 재료가 없는 S10은 호출하지 않는다)이고, 무료 등급 분당 제한을 피하려 호출 사이를 4초 띄워 **약 4분** 걸린다.

| 옵션 | 기본값 | 쓰임 |
|------|--------|------|
| `--arm` | (필수) | `before`=재설계 이전, `after`=현행(운영) 프롬프트, `pantry`=기본 양념 목록을 넓힌 안, `fruit-example`=규칙 1 예시를 사과로 되돌린 2026-09-07 이전 프롬프트, `no-sales-rule`=규칙 3(부족 재료 최소 1개)을 뺀 안, `no-category-rule`=규칙 4를 판매 카테고리 기준으로 바꾸기 전(2026-09-09 이전) 프롬프트 |
| `--repeats` | 3 | 시나리오당 반복 횟수 |
| `--scenarios` | 전체 | 부분 실행 (예: `--scenarios S4,S11`) |
| `--delay` | 4.0 | 호출 간 대기 초 |
| `--model` | `gemini-3.5-flash-lite` | 아래 "모델이 운영과 다르다" 참고 |
| `--out` | `docs/audits/cart-recipe/goldenset/` | 산출물 위치 |

`no-sales-rule`은 2026-09-09에 더한 팔이다 — 규칙 3("부족한 핵심 재료가 최소 1개는 반드시 있어야 한다")을 뺀 프롬프트로, 그 판매 유도 규칙이 요리 품질을 대가로 치르는지 보려고 만들었다.

**`after`는 항상 운영 프롬프트(`RecipeCandidateGenerator`)의 사본이다.** 자바 쪽 문구를 고치면 `AFTER_TEMPLATE`도 같은 턴에 맞춘다 — 어긋나면 "현행 품질"을 잰다는 이 도구의 전제가 깨진다. 나머지 팔은 전부 `AFTER_TEMPLATE`에서 한 곳만 치환해 만들므로, 두 팔의 차이가 그 한 곳뿐임이 코드로 보장된다.

> **2026-09-07 측정 파일의 이름 주의.** 그날 예시를 교체하면서 `raw-after-20260907-*.jsonl`은 **교체 전**, `raw-nofruit-20260907-*.jsonl`은 **교체 후**를 담았다. 교체를 반영한 뒤로 `after`는 교체 후를 가리키고, 교체 전을 다시 돌리려면 `fruit-example`을 쓴다. 비교 결과는 [`audits/cart-recipe/007`](../../docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md) M-1.

## 두 팔을 비교할 때

두 팔을 돌린 뒤에는 **채점 시트를 하나로 합쳐** 쓴다. 팔마다 나오는 시트를 따로 채우면 줄을 맞춰 가며 비교해야 하고, 같은 결과를 세 번 채점하게 된다.

```bash
python3 compare_arms_sheet.py <기준선.jsonl> <비교.jsonl> \
  --base-label "규칙 유지" --candidate-label "규칙 제거" \
  --out ../../docs/audits/cart-recipe/goldenset/grading-compare-YYYYMMDD.md
```

이 시트는 셋을 해 준다 — 같은 시나리오의 두 팔을 나란히 놓고, 똑같은 결과는 "3회"로 합치고, 계산으로 정해지는 축 둘(보유 활용·판매 연결)을 미리 채운다. 사람이 채울 칸이 450개에서 198개로 준다.

객관 지표만 먼저 보고 싶으면 채점 없이도 된다:

```bash
python3 analyse_arms.py <기준선.jsonl> <비교.jsonl>
```

## 산출물

| 파일 | 무엇 |
|------|------|
| `raw-{arm}-{시각}.jsonl` | 원본 응답·토큰 사용량. 재분석용이라 지우지 않는다 |
| `grading-{arm}-{시각}.md` | 사람이 5축을 손으로 채우는 채점 시트 |
| `raw-variants-{시각}.jsonl` | `experiment_variants.py`의 원본 (아래) |
| `raw-normalizer-{arm}-{시각}.jsonl` | `check_normalizer.py`의 원본 — 상품명별 답과 O/X (아래) |

**합격 판정은 스크립트가 하지 않는다.** 스펙이 사람 채점을 명시했고, LLM에게 채점을 시키면 자기 편향·장문 선호 같은 알려진 함정이 붙는다. 42건이면 눈으로 훑을 수 있는 양이다.

자동으로 나오는 건 채점과 무관한 수치뿐이다 — 출력 토큰 합계, 응답 후보 개수, 부족 재료가 0개인 실행 건수.

## 곁다리 스크립트 3개

메인 실행기와 달리 특정 질문 하나에 답하려고 만든 것들이다.

| 스크립트 | 무엇에 답하나 |
|----------|---------------|
| [`classify_missing.py`](classify_missing.py) | 부족 재료를 **카테고리 트리 기준**으로 분류해 "판매 가능 종류가 하나도 없는 추천" 비율을 낸다. 재료 → 카테고리 매핑이 가리키는 코드는 `scripts/init-db/02-init-categories.sql`에 실재해야 하고(실행마다 검사), 2026-09-06에 팔기로 한 김치·두부·장류는 예정 카테고리로 따로 센다. 값은 **종류 수준**이다 — 개별 상품의 판매 여부가 아니다([`008`](../../docs/audits/cart-recipe/008-open-problems-handoff.md) §3) |
| [`fruit_scenarios.py`](fruit_scenarios.py) | "과일을 재료에 넣으면 이상한 요리가 나오는가"를 옛 프롬프트와 현행 프롬프트에서 같은 조건으로 비교한다. 결과는 [`audits/cart-recipe/007`](../../docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md) M-2 |
| [`experiment_variants.py`](experiment_variants.py) | "대파·쪽파·실파처럼 비슷한 재료가 여러 개 담기면 추천이 나빠지는가", "이미 가진 재료의 다른 형태(마늘 vs 다진마늘)를 사라고 하는가"를 잰다. 결과는 [`audits/cart-recipe/003`](../../docs/audits/cart-recipe/003-pantry-staples-and-variants.md) |
| [`check_normalizer.py`](check_normalizer.py) | 레시피가 아니라 **그 앞단**을 잰다 — 상품명에서 재료명을 뽑는 프롬프트가 상품명 17종에 어떤 답을 내는지. `current` 팔은 프롬프트를 Java 파일에서 직접 읽어 운영과 어긋날 수 없다. 결과는 [`audits/cart-recipe/004`](../../docs/audits/cart-recipe/004-index-time-ingredient-name.md) |

```bash
python3 classify_missing.py ../../docs/audits/cart-recipe/goldenset/raw-*.jsonl
python3 experiment_variants.py
python3 check_normalizer.py --arm current
```

## 운영 코드와 맞춰 둔 것

측정 조건이 실제 서비스와 달라지면 결과를 못 쓴다. 아래는 [`RecipeCandidateGenerator`](../../src/main/java/com/barofarm/ai/recommend/infrastructure/llm/RecipeCandidateGenerator.java)·[`LlmClientConfig`](../../src/main/java/com/barofarm/ai/config/LlmClientConfig.java)에서 그대로 옮겼다. **Java 쪽이 바뀌면 같은 턴에 여기도 고친다.**

- `before` 프롬프트 전문과 응답 스키마 8필드 (`prompts.py`)
- temperature 0.7 · 응답 MIME `application/json` (모델은 예외 — 아래 참고)
- 호출 타임아웃 30초 · 재시도 총 3회 · 지수 백오프 1→10초 — **이건 배치 경로 값이다.** 운영 레시피 요청 경로는 3초·재시도 0회라, 실행기는 운영이 겪을 타임아웃을 관측하지 못한다. 대신 2026-09-09부터 호출마다 `elapsed_ms`를 기록해 3초 상한과 견준다(`audits/cart-recipe/010` 판단 항목 1)
- `chooseFirst`(이름이 있는 첫 후보)와 부족 재료 계산(`trim`·소문자·공백 제거 후 차집합)

## 모델

`gemini-3.5-flash-lite`를 쓴다. `application.yml`의 운영 기본값과 같은 값이라 **측정 조건이 운영과 일치한다.**

원래 운영 기본값은 `gemini-2.5-flash-lite`였는데 신규 발급 키로 404가 나서("no longer available to new users", 2026-09-06 실측) 구글이 지목한 후속 모델로 함께 옮겼다. 그래서 2026-09-06 측정의 **Before 수치는 "당시 운영이 내던 품질"이 아니다** — 두 팔이 같은 모델이라 Before/After 비교만 성립한다.

## 알려진 한계 — 카탈로그 적중률

"부족 재료 중 우리가 실제로 파는 재료의 비율"은 **자동으로 못 구한다.** `scripts/init-db/`에 카테고리 21종만 있고 상품 시드가 없어, 재료 단위 판매 목록이 로컬에 존재하지 않는다.

그래서 채점 시트에는 부족 재료가 등장 횟수와 함께 표로 나오고, 사람이 O/X를 표시하게 돼 있다. 판매 재료명 목록을 확보하면 이 폴더에 `catalog.txt`(한 줄에 하나, `#`는 주석)로 두면 되고, 그때부터는 적중률이 자동 계산된다.

이 수치가 카탈로그 그라운딩(부족 재료를 파는 재료로만 제한) 도입 여부를 정하는 근거다.

**대신 쓰는 기준은 카테고리 트리다 (2026-09-09 사용자 결정).** `catalog.txt`가 없는 동안 [`classify_missing.py`](classify_missing.py)가 부족 재료를 카테고리 21종(+예정 3종)에 매핑해 "종류 수준"으로 판정한다. 개별 상품 판정은 상품 시드가 오면 상품 인덱스 집계로 갈아탄다. 경위는 [`008`](../../docs/audits/cart-recipe/008-open-problems-handoff.md) §3.
