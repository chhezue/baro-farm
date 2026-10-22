#!/usr/bin/env python3
"""재료명 변종이 추천을 망치는지 확인하는 일회성 실험.

`audits/cart-recipe/002`가 S15(대파·쪽파·실파)의 부진 원인을 "같은 재료의 변종을 합치는
정규화가 없어서"라고 **추측**으로 남겨 뒀다. 추측을 근거로 코드를 고치지 않기 위해
(`.claude/rules/baro-ai-performance-refactor.md` 근거 기준) 두 가지를 따로 잰다.

- 입력측: 비슷한 변종이 여러 개 담기면 추천이 나빠지는가?  (E2 vs E3)
- 출력측: 이미 가진 재료의 변종 형태를 '부족'으로 내놓는가?  (E1 — 보유 "마늘" vs 응답 "다진마늘")

출력측은 사람 판단 없이 판정된다. 서버의 `IngredientProcessingUtil.normalizeForCompare`가
공백 제거·소문자화만 하는 완전일치 비교라, "다진마늘"과 "마늘"은 서로 다른 재료로 계산된다.

실행:
    export BARO_AI_GEMINI_KEY="..."
    python3 baro-ai/scripts/goldenset/experiment_variants.py
"""
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import prompts  # noqa: E402
from run_goldenset import (  # noqa: E402
    DEFAULT_MODEL,
    DEFAULT_OUT,
    call_gemini,
    choose_first,
    extract_candidates,
    missing_core,
    normalize,
)

REPEATS = 5
DELAY_SEC = 4.0
ARM = "after"  # 운영과 같은 프롬프트로 잰다

CARTS = [
    ("E1", ["마늘", "양파", "돼지고기"], "보유 재료의 변종 형태가 부족 재료로 나오는지 (마늘 vs 다진마늘)"),
    ("E2", ["대파", "쪽파", "실파"], "변종 3개 그대로 — 현행 S15"),
    ("E3", ["대파"], "변종을 하나로 합친 경우"),
]


def variant_conflicts(owned, missing):
    """보유 재료를 부분 문자열로 품은 부족 재료를 찾는다 (예: 보유 '마늘' vs 부족 '다진마늘')."""
    hits = []
    for m in missing:
        for o in owned:
            nm, no = normalize(m), normalize(o)
            if nm != no and (no in nm or nm in no):
                hits.append((o, m))
    return hits


def main() -> int:
    api_key = os.environ.get("BARO_AI_GEMINI_KEY", "").strip()
    if not api_key:
        print("BARO_AI_GEMINI_KEY 환경변수가 비어 있습니다.", file=sys.stderr)
        return 2

    model = os.environ.get("SPRING_AI_GEMINI_MODEL", DEFAULT_MODEL)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path(DEFAULT_OUT)
    out_dir.mkdir(parents=True, exist_ok=True)
    raw_path = out_dir / f"raw-variants-{stamp}.jsonl"

    records = []
    with raw_path.open("w", encoding="utf-8") as fp:
        for cart_id, owned, intent in CARTS:
            for run_no in range(1, REPEATS + 1):
                prompt = prompts.build_prompt(ARM, owned)
                try:
                    response = call_gemini(api_key, model, prompt, prompts.ARMS[ARM]["schema"])
                    chosen = choose_first(extract_candidates(response))
                except Exception as exc:  # 실패도 기록에 남긴다
                    chosen, err = None, str(exc)
                else:
                    err = None

                missing = missing_core(chosen, owned) if chosen else []
                rec = {
                    "cart": cart_id,
                    "run": run_no,
                    "owned": owned,
                    "intent": intent,
                    "model": model,
                    "recipeName": (chosen or {}).get("recipeName"),
                    "core": (chosen or {}).get("recipeIngredientsCore"),
                    "missing_core": missing,
                    "variant_conflicts": variant_conflicts(owned, missing),
                    "error": err,
                }
                records.append(rec)
                fp.write(json.dumps(rec, ensure_ascii=False) + "\n")
                fp.flush()
                print(f"{cart_id}#{run_no} {rec['recipeName']} / 부족: {missing}"
                      f"{' ⚠ ' + str(rec['variant_conflicts']) if rec['variant_conflicts'] else ''}",
                      flush=True)
                time.sleep(DELAY_SEC)

    print(f"\n원본: {raw_path}")
    for cart_id, owned, intent in CARTS:
        rs = [r for r in records if r["cart"] == cart_id]
        conflicts = sum(1 for r in rs if r["variant_conflicts"])
        names = [r["recipeName"] for r in rs]
        print(f"\n[{cart_id}] {intent}")
        print(f"  보유: {owned}")
        print(f"  요리: {names}")
        print(f"  보유 변종을 부족으로 낸 실행: {conflicts}/{len(rs)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
