#!/usr/bin/env python3
"""레시피 추천 프롬프트 품질 측정용 골든셋 실행기.

docs/specs/cart-recipe/recipe-quality-redesign.md(Approved)의 "검증 시나리오"를 실행한다.
Gemini API 키 하나만 있으면 되고 Elasticsearch·Kafka·baro-shopping은 필요 없다 —
프롬프트 문자열을 만들어 Gemini에 직접 던지기 때문이다.

    export BARO_AI_GEMINI_KEY="..."
    python3 run_goldenset.py --arm before      # 기준선 먼저
    python3 run_goldenset.py --arm after       # 프롬프트 반영 후

산출물 2개가 --out 디렉터리에 떨어진다.
  raw-{arm}-{시각}.jsonl       원본 응답·토큰 사용량 (재분석용)
  grading-{arm}-{시각}.md      사람이 5축을 손으로 채점하는 시트

합격 판정은 스크립트가 하지 않는다. 스펙이 사람 채점을 명시했고,
LLM에게 채점을 시키면 자기 편향·장문 선호가 붙기 때문이다.
"""

import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

from prompts import build_prompt, ARMS

API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

# LlmClientConfig와 같은 값으로 맞춘다 — 측정 조건이 운영과 달라지지 않게.
# 예외: 모델. 운영 설정은 gemini-2.5-flash-lite지만 신규 발급 키로는 호출이 404로 막힌다
# ("no longer available to new users", 2026-09-06 실측). 구글이 지목한 후속 모델을 쓴다.
# 두 팔이 같은 모델이면 Before/After 비교는 성립하되, Before가 운영 현행과 같지는 않다.
DEFAULT_MODEL = "gemini-3.5-flash-lite"
TEMPERATURE = 0.7
CALL_TIMEOUT_SEC = 30
RETRY_TOTAL_ATTEMPTS = 3
RETRY_INITIAL_DELAY_SEC = 1.0
RETRY_MAX_DELAY_SEC = 10.0

# 무료 등급 분당 호출 제한에 걸리지 않게 호출 사이를 띄운다.
DEFAULT_DELAY_SEC = 4.0

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_OUT = SCRIPT_DIR.parent.parent / "docs" / "audits" / "cart-recipe" / "goldenset"
CATALOG_FILE = SCRIPT_DIR / "catalog.txt"

# 스펙 "골든셋 — 장바구니 15종". 재료명은 scripts/init-db/02-init-categories.sql 카테고리 기준.
SCENARIOS = [
    ("S1", ["계란"], "재료 1개 — 부족 재료가 필연인 최소 경우"),
    ("S2", ["표고버섯"], "주재료로 삼기 어려운 재료 1개"),
    ("S3", ["돼지고기", "양파", "대파"], "궁합이 좋은 조합 — 기준선"),
    ("S4", ["오이", "쌀", "땅콩"], "궁합이 나쁜 조합 — '오이 파스타' 재현"),
    ("S5", ["상추", "깻잎", "치커리"], "샐러드 채소만"),
    ("S6", ["감자", "당근", "무"], "뿌리채소만"),
    ("S7", ["계란", "우유", "삼겹살"], "축산물만"),
    ("S8", ["배추", "양파", "대파", "감자", "당근", "애호박", "시금치",
            "돼지고기", "계란", "우유", "쌀"], "큰 장바구니 11종 — 부족 재료가 자연 발생하는지"),
    ("S9", ["쌀", "보리"], "곡류만"),
    ("S10", [], "보유 재료 0개 — 서버가 LLM을 부르지 않고 빈 응답"),
    ("S11", ["사과", "계란"], "과일 제외 해제 판단용 — '사과 계란 찜' 케이스"),
    ("S12", ["호두", "아몬드"], "견과류만"),
    ("S13", ["더덕", "도라지"], "특용작물"),
    ("S14", ["계란", "감자"], "궁합이 애매한 2개"),
    ("S15", ["대파", "쪽파", "실파"], "사실상 같은 재료 — 정규화·중복 제거 확인"),
]


def normalize(name: str) -> str:
    """CandidateRecipePlan.normalizeForCompare와 같은 규칙 — trim·소문자·공백 제거."""
    return "".join(name.split()).lower()


def call_gemini(api_key: str, model: str, prompt: str, schema: dict,
                temperature: float = TEMPERATURE) -> dict:
    """Gemini를 1회 호출한다. 429·5xx·타임아웃은 지수 백오프로 재시도한다.

    temperature 기본값은 레시피 후보 생성에 맞춘 0.7이다. 색인 시점 재료명 추출은
    운영이 0.0을 쓰므로(`ProductIngredientNormalizer`) 그쪽 검증은 값을 넘겨 맞춘다.
    """
    body = json.dumps({
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": temperature,
            "responseMimeType": "application/json",
            "responseSchema": schema,
        },
    }).encode("utf-8")

    delay = RETRY_INITIAL_DELAY_SEC
    last_error = None
    for attempt in range(1, RETRY_TOTAL_ATTEMPTS + 1):
        request = urllib.request.Request(
            API_URL.format(model=model),
            data=body,
            headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=CALL_TIMEOUT_SEC) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:300]
            last_error = f"HTTP {e.code}: {detail}"
            # 4xx 중 재시도가 의미 있는 건 429뿐이다. 400·403은 즉시 멈춰야 원인이 보인다.
            if e.code != 429 and e.code < 500:
                raise RuntimeError(last_error) from e
        except Exception as e:
            last_error = f"{type(e).__name__}: {e}"

        if attempt < RETRY_TOTAL_ATTEMPTS:
            sleep_for = min(delay, RETRY_MAX_DELAY_SEC)
            print(f"    재시도 {attempt}/{RETRY_TOTAL_ATTEMPTS - 1} ({last_error}) — {sleep_for:.1f}초 대기",
                  file=sys.stderr)
            time.sleep(sleep_for)
            delay *= 2

    raise RuntimeError(f"{RETRY_TOTAL_ATTEMPTS}회 모두 실패 — {last_error}")


def extract_candidates(response: dict) -> list[dict]:
    """구조화 출력 JSON을 후보 목록으로 푼다. 형태가 어긋나면 빈 목록."""
    try:
        text = response["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError):
        return []
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        return []
    candidates = parsed.get("candidates")
    return candidates if isinstance(candidates, list) else []


def choose_first(candidates: list[dict]) -> dict | None:
    """RecipeCandidates.chooseFirst — 이름이 있는 첫 후보. 실제로 사용자에게 나가는 하나다."""
    for candidate in candidates:
        if isinstance(candidate, dict) and (candidate.get("recipeName") or "").strip():
            return candidate
    return None


def missing_core(candidate: dict, owned: list[str]) -> list[str]:
    """서버가 계산하는 부족 핵심 재료 — 핵심 재료에서 보유 재료를 뺀 것."""
    owned_norm = {normalize(o) for o in owned}
    core = candidate.get("recipeIngredientsCore") or []
    return [c for c in core if normalize(c) not in owned_norm]


def load_catalog() -> set[str] | None:
    """판매 재료명 목록. 없으면 적중률 계산을 건너뛴다(상품 시드가 로컬에 없다)."""
    if not CATALOG_FILE.exists():
        return None
    names = set()
    for line in CATALOG_FILE.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            names.add(normalize(line))
    return names or None


def run(args) -> int:
    api_key = os.environ.get("BARO_AI_GEMINI_KEY", "").strip()
    if not api_key:
        print("BARO_AI_GEMINI_KEY 환경변수가 비어 있습니다.\n"
              "  export BARO_AI_GEMINI_KEY=\"...\" 후 다시 실행하세요.\n"
              "  (~/.zshrc에 넣었다면 새 터미널을 열거나 `source ~/.zshrc`)", file=sys.stderr)
        return 1

    selected = None
    if args.scenarios:
        selected = {s.strip().upper() for s in args.scenarios.split(",")}

    scenarios = [s for s in SCENARIOS if selected is None or s[0] in selected]
    if not scenarios:
        print(f"해당하는 시나리오가 없습니다: {args.scenarios}", file=sys.stderr)
        return 1

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    raw_path = out_dir / f"raw-{args.arm}-{stamp}.jsonl"
    grading_path = out_dir / f"grading-{args.arm}-{stamp}.md"

    llm_scenarios = [s for s in scenarios if s[1]]
    total_calls = len(llm_scenarios) * args.repeats
    print(f"[{args.arm}] 시나리오 {len(scenarios)}개 × {args.repeats}회 = LLM 호출 {total_calls}회 "
          f"(모델 {args.model}, 호출 간 {args.delay}초)")

    records = []
    done = 0
    with raw_path.open("w", encoding="utf-8") as raw_file:
        for code, owned, intent in scenarios:
            for run_no in range(1, args.repeats + 1):
                record = {
                    "arm": args.arm, "scenario": code, "run": run_no,
                    "owned": owned, "intent": intent, "model": args.model,
                }

                if not owned:
                    # 보유 재료가 0개면 서버가 LLM을 부르지 않고 빈 응답을 낸다.
                    # 호출해 봐야 실제 동작과 다른 것을 재게 되므로 건너뛴다.
                    record.update(skipped="보유 재료 0개 — 서버가 LLM 미호출", chosen=None)
                    records.append(record)
                    raw_file.write(json.dumps(record, ensure_ascii=False) + "\n")
                    continue

                done += 1
                print(f"  [{done}/{total_calls}] {code} run{run_no} ({', '.join(owned)})")
                prompt = build_prompt(args.arm, owned)
                try:
                    response = call_gemini(api_key, args.model, prompt, ARMS[args.arm]["schema"])
                except Exception as e:
                    record.update(error=str(e), chosen=None)
                    records.append(record)
                    raw_file.write(json.dumps(record, ensure_ascii=False) + "\n")
                    raw_file.flush()
                    print(f"      실패: {e}", file=sys.stderr)
                    continue

                candidates = extract_candidates(response)
                chosen = choose_first(candidates)
                usage = response.get("usageMetadata", {})
                record.update(
                    candidate_count=len(candidates),
                    chosen=chosen,
                    missing_core=missing_core(chosen, owned) if chosen else [],
                    prompt_tokens=usage.get("promptTokenCount"),
                    output_tokens=usage.get("candidatesTokenCount"),
                    total_tokens=usage.get("totalTokenCount"),
                )
                records.append(record)
                raw_file.write(json.dumps(record, ensure_ascii=False) + "\n")
                raw_file.flush()

                if done < total_calls:
                    time.sleep(args.delay + random.uniform(0, 0.5))

    write_grading_sheet(grading_path, args, records)
    print(f"\n원본:     {raw_path}")
    print(f"채점 시트: {grading_path}")
    print("\n채점 시트의 5축을 사람이 직접 채운 뒤 Before/After를 비교하세요.")
    return 0


def write_grading_sheet(path: Path, args, records: list[dict]) -> None:
    """사람이 5축을 손으로 채우는 마크다운 시트를 만든다."""
    catalog = load_catalog()
    lines = [
        f"# 골든셋 채점 시트 — {args.arm}",
        "",
        f"실행: {datetime.now():%Y-%m-%d %H:%M} · 모델 `{args.model}` · temperature {TEMPERATURE} · "
        f"시나리오당 {args.repeats}회",
        "",
        "5축을 **사람이** 채운다(O/X). 모든 축을 통과해야 합격이다 — "
        "판정 기준은 [`recipe-quality-redesign.md`]"
        "(../../../specs/cart-recipe/recipe-quality-redesign.md) \"합격 기준\" 절.",
        "",
        "1 실재성 · 2 재료 정합성 · 3 보유 활용 · 4 조리법 타당성 · 5 판매 연결(부족 재료 ≥ 1)",
        "",
        "| # | 회차 | 장바구니 | 추천 요리 | 핵심 재료 | 부족 재료(서버 계산) | 1 | 2 | 3 | 4 | 5 | 합격 |",
        "|---|------|----------|-----------|-----------|----------------------|---|---|---|---|---|------|",
    ]

    for r in records:
        owned = ", ".join(r["owned"]) or "(없음)"
        if r.get("skipped"):
            lines.append(f"| {r['scenario']} | {r['run']} | {owned} | _{r['skipped']}_ | | | - | - | - | - | - | - |")
            continue
        if r.get("error"):
            lines.append(f"| {r['scenario']} | {r['run']} | {owned} | **호출 실패** | {r['error'][:60]} | | | | | | | |")
            continue
        chosen = r.get("chosen")
        if not chosen:
            lines.append(f"| {r['scenario']} | {r['run']} | {owned} | **빈 응답** | | | | | | | | |")
            continue
        core = ", ".join(chosen.get("recipeIngredientsCore") or [])
        missing = ", ".join(r.get("missing_core") or []) or "(없음)"
        lines.append(
            f"| {r['scenario']} | {r['run']} | {owned} | {chosen.get('recipeName', '')} | "
            f"{core} | {missing} | | | | | | |"
        )

    ok = [r for r in records if r.get("chosen")]
    tokens = [r.get("output_tokens") or 0 for r in ok]
    lines += [
        "",
        "## 자동 집계 (채점과 별개)",
        "",
        f"- 호출 성공 {len(ok)}건 / 실패 {len([r for r in records if r.get('error')])}건 / "
        f"빈 응답 {len([r for r in records if not r.get('chosen') and not r.get('error') and not r.get('skipped')])}건",
        f"- 출력 토큰 합계 **{sum(tokens):,}** · 평균 {(sum(tokens) / len(tokens) if tokens else 0):.0f}",
        f"- 응답 후보 개수 평균 {(sum(r.get('candidate_count') or 0 for r in ok) / len(ok) if ok else 0):.2f}",
        f"- 부족 재료 0개인 실행 {len([r for r in ok if not r.get('missing_core')])}건 "
        f"(합격 기준 5축 위반 후보)",
    ]

    all_missing: dict[str, int] = {}
    for r in ok:
        for name in r.get("missing_core") or []:
            all_missing[name] = all_missing.get(name, 0) + 1

    lines += ["", "### 부족 재료 카탈로그 적중률", ""]
    if not all_missing:
        lines.append("부족 재료가 하나도 나오지 않았다.")
    elif catalog:
        hit = sum(count for name, count in all_missing.items() if normalize(name) in catalog)
        total = sum(all_missing.values())
        lines.append(f"`catalog.txt` 기준 **{hit}/{total} = {hit / total * 100:.1f}%**")
    else:
        lines += [
            "`catalog.txt`가 없어 자동 계산을 건너뛰었다 — 로컬에 상품 시드가 없다"
            "(`scripts/init-db/`에는 카테고리만 있다).",
            "아래 목록에서 **우리가 실제로 파는 재료**에 표시하고 비율을 적으면 "
            "카탈로그 그라운딩 도입 여부의 근거가 된다.",
            "",
            "| 부족 재료 | 등장 횟수 | 판매 O/X |",
            "|-----------|-----------|----------|",
        ]
        for name, count in sorted(all_missing.items(), key=lambda kv: -kv[1]):
            lines.append(f"| {name} | {count} | |")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="레시피 추천 골든셋 실행기")
    parser.add_argument("--arm", required=True, choices=sorted(ARMS),
                        help="before=재설계 이전 프롬프트, after=현행(운영) 프롬프트, "
                             "pantry=기본 양념 목록을 넓힌 후속 라운드 안")
    parser.add_argument("--repeats", type=int, default=3, help="시나리오당 반복 횟수 (기본 3)")
    parser.add_argument("--scenarios", help="쉼표로 구분한 부분 실행 (예: S4,S11)")
    parser.add_argument("--model", default=os.environ.get("SPRING_AI_GEMINI_MODEL", DEFAULT_MODEL))
    parser.add_argument("--delay", type=float, default=DEFAULT_DELAY_SEC,
                        help="호출 간 대기 초 (기본 4 — 무료 등급 분당 제한 회피)")
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    return run(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())
