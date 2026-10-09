#!/usr/bin/env python3
"""색인 시점 재료명 추출 프롬프트를 상품명 골든셋으로 검증한다.

`ProductIngredientNormalizer`는 상품명에서 요리 재료명을 뽑아 `ProductDocument.ingredientName`에
넣는다. 이 값이 틀리면 "장바구니에 무엇이 들었나"가 통째로 틀어지므로, 레시피 품질보다 앞단이다.

2026-09-06에 김치·두부·장류를 팔기로 정하면서, 농산물만 전제로 쓰인 이 프롬프트가 새 상품에서
어떻게 동작하는지 확인할 필요가 생겼다. 프롬프트 문장을 읽고 "위험해 보인다"고 판단하는 대신
실제로 돌려 보려고 만든 스크립트다.

실행:
    export BARO_AI_GEMINI_KEY="..."
    python3 baro-ai/scripts/goldenset/check_normalizer.py --arm current
    python3 baro-ai/scripts/goldenset/check_normalizer.py --arm revised
"""
import argparse
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from normalizer_prompts import ARMS, build_prompt  # noqa: E402
from run_goldenset import DEFAULT_MODEL, DEFAULT_OUT, call_gemini  # noqa: E402

DELAY_SEC = 4.0
SCHEMA = {
    "type": "OBJECT",
    "properties": {"normalizedIngredient": {"type": "STRING"}},
    "required": ["normalizedIngredient"],
}

# (상품명, 카테고리명, 허용 답, 메모)
# 허용 답은 "요리 재료로 이 이름이 오면 레시피가 성립하는가" 기준이며 필자 판단이다.
CASES = [
    # --- 기존 농산물: 이번 변경으로 나빠지면 안 되는 대조군 ---
    ("곰곰 무항생제 신선한 대란, 30구, 1개", "계란", {"계란"}, "대조군"),
    ("제주 서귀포 고당도 노지 감귤 3kg (박스)", "제철 과일", {"감귤"}, "대조군"),
    ("횡성 한우 등심 300g 1등급", "육류", {"소고기"}, "대조군"),
    ("친환경 흙대파 1단 (700g 내외)", "국/찌개용 채소", {"대파"}, "대조군"),
    ("강원도 감자 5kg 특품", "뿌리채소", {"감자"}, "대조군"),
    ("햇 국산 깐마늘 500g", "뿌리채소", {"마늘"}, "대조군 — 가공 형태(깐)"),
    # --- 김치류 ---
    ("종가집 포기김치 3kg", "김치류", {"김치", "배추김치"},
     "'배추'는 원재료라 실패. '포기김치'도 실패 — 레시피는 '김치'라고 쓴다"),
    ("아삭한 열무김치 2kg", "김치류", {"열무김치", "김치"}, "'열무'로 답하면 실패"),
    ("전라도 묵은지 5kg", "김치류", {"묵은지", "김치"}, "'배추'로 답하면 실패"),
    ("시원한 백김치 1.5kg", "김치류", {"백김치", "김치"}, "'배추'로 답하면 실패"),
    # --- 두부·콩가공 ---
    ("국산콩 100% 부침용 두부 300g", "두부·콩가공", {"두부"}, "'콩'으로 답하면 실패"),
    ("순두부 350g (찌개용)", "두부·콩가공", {"순두부"}, "'두부'로 답하면 요리가 달라짐"),
    ("사각 유부 200g", "두부·콩가공", {"유부"}, "'두부'로 답하면 실패"),
    # --- 장류 ---
    ("전통 재래식 된장 1kg (국산 콩)", "장류", {"된장"}, "'콩'으로 답하면 실패"),
    ("순창 태양초 고추장 500g", "장류", {"고추장"}, "'고추'로 답하면 실패"),
    ("조선간장(국간장) 900ml", "장류", {"간장", "국간장", "조선간장"}, "'콩'으로 답하면 실패"),
    ("맛있는 쌈장 450g", "장류", {"쌈장"}, "'된장'으로 답하면 실패"),
]


def main() -> int:
    parser = argparse.ArgumentParser(description="재료명 추출 프롬프트 검증기")
    parser.add_argument("--arm", required=True, choices=sorted(ARMS))
    parser.add_argument("--model", default=os.environ.get("SPRING_AI_GEMINI_MODEL", DEFAULT_MODEL))
    parser.add_argument("--delay", type=float, default=DELAY_SEC)
    args = parser.parse_args()

    api_key = os.environ.get("BARO_AI_GEMINI_KEY", "").strip()
    if not api_key:
        print("BARO_AI_GEMINI_KEY 환경변수가 비어 있습니다.", file=sys.stderr)
        return 2

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path(DEFAULT_OUT)
    out_dir.mkdir(parents=True, exist_ok=True)
    raw_path = out_dir / f"raw-normalizer-{args.arm}-{stamp}.jsonl"

    ok = 0
    with raw_path.open("w", encoding="utf-8") as fp:
        for product, category, allowed, memo in CASES:
            prompt = build_prompt(args.arm, product, category)
            try:
                # 운영(`ProductIngredientNormalizer`)이 0.0을 쓴다 — 추출이지 창작이 아니라서다.
                res = call_gemini(api_key, args.model, prompt, SCHEMA, temperature=0.0)
                text = res["candidates"][0]["content"]["parts"][0]["text"]
                answer = (json.loads(text).get("normalizedIngredient") or "").strip()
                err = None
            except Exception as exc:
                answer, err = "", str(exc)

            passed = answer in allowed
            ok += passed
            fp.write(json.dumps({
                "arm": args.arm, "product": product, "category": category,
                "answer": answer, "allowed": sorted(allowed), "pass": passed,
                "memo": memo, "error": err,
            }, ensure_ascii=False) + "\n")
            fp.flush()
            mark = "O" if passed else "X"
            print(f"  [{mark}] {product[:34]:<34} / {category:<12} -> {answer!r}"
                  f"{'' if passed else '   기대: ' + '|'.join(sorted(allowed))}", flush=True)
            time.sleep(args.delay)

    print(f"\n{args.arm}: {ok}/{len(CASES)} 통과")
    print(f"원본: {raw_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
