"""두 팔의 골든셋 결과를 **한 장으로 합쳐** 사람이 채점하기 쉽게 만든다.

기존 시트는 팔마다 파일이 따로였고, 한 파일에 42줄 × 5축을 전부 사람이 채우게 돼 있었다.
두 팔을 비교하려면 파일 둘을 나란히 열고 줄을 맞춰 봐야 했고, 채울 칸이 420개였다.

이 스크립트는 셋을 바꾼다.

1. **한 파일에 두 팔을 나란히** 놓는다. 같은 시나리오가 붙어 있어 눈으로 바로 비교된다.
2. **똑같은 결과는 합친다.** 3회 돌려 같은 요리·같은 재료가 나오면 한 줄에 "3회"로 적는다.
   같은 것을 세 번 채점할 이유가 없다.
3. **계산으로 정해지는 축은 미리 채운다.** 보유 활용(축 3)은 서버가 재료를 대조해 확정하는 값이고,
   판매 연결(축 5)은 2026-09-09부터 "판매 가능한 종류의 부족 재료가 최소 1개"다 — `classify_missing.py`의
   카테고리 매핑으로 계산하며 예외(정체성 재료 등)를 두지 않는다. 사람은 나머지 셋만 본다.

사용: python3 compare_arms_sheet.py <기준선.jsonl> <비교.jsonl> [--out 경로]
"""

import argparse
import json
import os
import re
import sys
from collections import OrderedDict, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classify_missing  # noqa: E402 — 축 5(판매 연결)를 카테고리 기준으로 판정


def normalize_for_compare(s):
    """자바 IngredientProcessingUtil.normalizeForCompare와 같은 규칙."""
    return re.sub(r"\s+", "", (s or "").strip().lower())


def normalize_list(items):
    out, seen = [], set()
    for x in items or []:
        t = (x or "").strip()
        if t and t not in seen:
            seen.add(t)
            out.append(t)
    return out


def load(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def summarize(row):
    """한 응답에서 채점에 필요한 값을 뽑고, 계산으로 정해지는 두 축을 미리 판정한다."""
    chosen = row.get("chosen") or {}
    name = chosen.get("recipeName") or ""
    owned = normalize_list(row.get("owned"))
    owned_norm = {normalize_for_compare(x) for x in owned}
    core = normalize_list(chosen.get("recipeIngredientsCore"))

    used = [c for c in core if normalize_for_compare(c) in owned_norm]
    missing = [c for c in core if normalize_for_compare(c) not in owned_norm]

    return {
        "name": name or "(이름 없음)",
        "core": core,
        "used": used,
        "missing": missing,
        # 축 3: 프롬프트 규칙 2 — 보유 재료 중 최소 1개는 쓴다
        "axis3": "O" if used else "X",
        # 축 5: 판매 가능한 종류(실카테고리)의 부족 재료가 최소 1개 있다 — 카드가 붙을 수 있는 추천인가.
        # 2026-09-09까지는 "부족 재료가 1개 이상"이었는데, 그러면 마요네즈만 부족해도 O가 됐다.
        "axis5": "O" if any(classify_missing.classify(m)[0] == "REAL" for m in missing) else "X",
        "ok": bool(name),
    }


def group(rows):
    """(시나리오) → 같은 결과끼리 합친 목록. 키는 요리명 + 핵심 재료 조합."""
    by_scenario = OrderedDict()
    for row in rows:
        s = summarize(row)
        key = (s["name"], tuple(s["core"]))
        bucket = by_scenario.setdefault(row["scenario"], {"owned": row.get("owned") or [],
                                                          "intent": row.get("intent", ""),
                                                          "items": OrderedDict()})
        entry = bucket["items"].setdefault(key, {**s, "runs": 0})
        entry["runs"] += 1
    return by_scenario


def scenario_order(key):
    m = re.match(r"S(\d+)", key)
    return int(m.group(1)) if m else 999


def render(base_rows, cand_rows, base_label, cand_label):
    base, cand = group(base_rows), group(cand_rows)
    scenarios = sorted(set(base) | set(cand), key=scenario_order)

    out = []
    out.append(f"# 골든셋 채점 — {base_label} vs {cand_label}\n")
    out.append("두 팔을 한 장에 놓고, 똑같은 결과는 합치고, 계산으로 정해지는 축은 미리 채웠다.\n")
    out.append("## 채울 것은 세 칸뿐이다\n")
    out.append("각 줄의 `실재`·`정합`·`조리` 칸에 **O 또는 X**를 적는다. 나머지는 이미 채워져 있다.\n")
    out.append("| 축 | 무엇을 보나 | 누가 |")
    out.append("|---|---|---|")
    out.append("| **실재** | 실제로 사람들이 만들어 먹는 요리인가. 지어낸 요리가 아닌가 | 사람 |")
    out.append("| **정합** | 적힌 핵심 재료가 그 요리에 실제로 들어가는가 | 사람 |")
    out.append("| **조리** | 조리법 단계가 말이 되는가 | 사람 |")
    out.append("| 보유활용 | 장바구니 재료를 최소 1개 쓰는가 | 계산 (채워져 있음) |")
    out.append("| 판매연결 | 우리가 파는 종류의 부족 재료가 최소 1개 있는가 — 카테고리 기준, 예외 없음 | 계산 (채워져 있음) |")
    out.append("")
    out.append("> **비교할 때 주의:** `판매연결`은 프롬프트 규칙이 직접 겨냥하는 축이라 두 팔이 다르게 나오는 게 정상이다.")
    out.append("> \"규칙이 요리 품질을 해쳤나\"는 **`실재`·`정합`·`조리` 셋**으로, \"살 게 생겼나\"는 `판매연결`로 따로 본다.")
    out.append("")
    out.append("**`회`는 같은 결과가 3회 중 몇 번 나왔는지다.** 같은 줄은 한 번만 채우면 된다.\n")
    out.append("---\n")

    human_cells = 0
    for s in scenarios:
        b = base.get(s, {"owned": [], "intent": "", "items": {}})
        c = cand.get(s, {"owned": [], "intent": "", "items": {}})
        owned = b["owned"] or c["owned"]
        intent = b["intent"] or c["intent"]

        out.append(f"## {s} — 장바구니: {', '.join(owned) if owned else '(비어 있음)'}")
        if intent:
            out.append(f"\n_{intent}_\n")
        out.append("")
        out.append("| 팔 | 회 | 추천 요리 | 핵심 재료 | 부족 재료 | 실재 | 정합 | 조리 | 보유활용 | 판매연결 |")
        out.append("|---|---|---|---|---|---|---|---|---|---|")

        for label, bucket in ((base_label, b), (cand_label, c)):
            if not bucket["items"]:
                out.append(f"| {label} | — | (응답 없음) | | | | | | | |")
                continue
            for item in bucket["items"].values():
                human_cells += 3
                out.append(
                    f"| {label} | {item['runs']}회 | **{item['name']}** | "
                    f"{', '.join(item['core']) or '—'} | {', '.join(item['missing']) or '없음'} | "
                    f"  |   |   | {item['axis3']} | {item['axis5']} |")
        out.append("")

    out.append("---\n")
    out.append("## 다 채운 뒤\n")
    out.append("아래 표에 팔별로 세어 적는다. 합격은 **다섯 축 전부 O**다 — 사람이 채운 셋과 계산된 둘.\n")
    out.append("| 팔 | 채점한 줄 | 다섯 축 모두 O | 합격률 |")
    out.append("|---|---|---|---|")
    out.append(f"| {base_label} | | | |")
    out.append(f"| {cand_label} | | | |")
    out.append("")
    out.append(f"> 사람이 채울 칸: **{human_cells}개**. 합치기 전 기준으로는 "
               f"{(len(base_rows) + len(cand_rows)) * 5}개였다.\n")
    return "\n".join(out), human_cells


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("candidate")
    ap.add_argument("--base-label", default="규칙 유지")
    ap.add_argument("--candidate-label", default="규칙 제거")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    base_rows, cand_rows = load(args.base), load(args.candidate)
    text, cells = render(base_rows, cand_rows, args.base_label, args.candidate_label)

    out = args.out or os.path.join(os.path.dirname(args.base), "grading-compare.md")
    with open(out, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"채점 시트: {out}")
    print(f"사람이 채울 칸: {cells}개 (합치기 전 {(len(base_rows) + len(cand_rows)) * 5}개)")


if __name__ == "__main__":
    main()
