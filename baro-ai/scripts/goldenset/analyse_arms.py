"""골든셋 두 팔의 **객관 지표**를 비교한다. 사람 채점(합격률)과는 별개다.

합격 판정은 스펙이 사람 채점으로 못박아 두었고 이 스크립트는 그것을 대신하지 않는다.
여기서 재는 것은 서버가 계산으로 확정할 수 있는 값뿐이다 — 부족 재료 개수, 보유 재료 사용률,
요리 이름의 다양성. 판정에 쓰는 재료명 대조는 자바(`IngredientProcessingUtil`·`CandidateRecipePlan.from`)와
같은 규칙이다: 앞뒤 공백 제거 → 소문자화 → 내부 공백 제거 → 정확 일치.

사용: python3 analyse_arms.py <기준선.jsonl> <비교.jsonl>
"""

import json
import re
import statistics
import sys
from collections import defaultdict


def normalize_for_compare(s):
    """자바 IngredientProcessingUtil.normalizeForCompare와 같은 규칙."""
    if s is None:
        return ""
    return re.sub(r"\s+", "", s.strip().lower())


def normalize_list(items):
    """자바 normalizeList — trim, 빈 값 제거, 중복 제거(순서 유지)."""
    out, seen = [], set()
    for x in items or []:
        if x is None:
            continue
        t = x.strip()
        if not t or t in seen:
            continue
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


def metrics(rows, label):
    missing_counts, used_ratios, core_counts = [], [], []
    zero_missing = 0
    by_scenario = defaultdict(set)
    failed = 0

    for r in rows:
        chosen = r.get("chosen")
        if not chosen or not chosen.get("recipeName"):
            failed += 1
            continue
        owned = r.get("owned") or []
        owned_norm = {normalize_for_compare(x) for x in normalize_list(owned)}
        core = normalize_list(chosen.get("recipeIngredientsCore"))

        missing = [c for c in core if normalize_for_compare(c) not in owned_norm]
        used = [c for c in core if normalize_for_compare(c) in owned_norm]

        missing_counts.append(len(missing))
        core_counts.append(len(core))
        if len(missing) == 0:
            zero_missing += 1
        if owned:
            used_ratios.append(len(used) / len(owned))
        by_scenario[r["scenario"]].add(chosen["recipeName"])

    n = len(missing_counts)
    distinct = [len(v) for v in by_scenario.values()]
    return {
        "arm": label,
        "응답 수": n,
        "이름 없는 응답": failed,
        "평균 부족 재료 개수": round(statistics.mean(missing_counts), 3) if n else 0,
        "부족 재료 0개 비율": round(zero_missing / n, 3) if n else 0,
        "평균 핵심 재료 개수": round(statistics.mean(core_counts), 3) if n else 0,
        "보유 재료 사용률 평균": round(statistics.mean(used_ratios), 3) if used_ratios else 0,
        "시나리오당 서로 다른 요리 수(3회 기준)": round(statistics.mean(distinct), 3) if distinct else 0,
    }


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    base = metrics(load(sys.argv[1]), f"기준선 ({sys.argv[1].rsplit('/', 1)[-1]})")
    cand = metrics(load(sys.argv[2]), f"비교 ({sys.argv[2].rsplit('/', 1)[-1]})")

    print(json.dumps(base, ensure_ascii=False, indent=2))
    print(json.dumps(cand, ensure_ascii=False, indent=2))

    print("\n=== 기준선 → 비교 ===")
    print(f"부족 재료 0개 비율: {base['부족 재료 0개 비율']:.1%} → {cand['부족 재료 0개 비율']:.1%}")
    print(f"평균 부족 재료 개수: {base['평균 부족 재료 개수']} → {cand['평균 부족 재료 개수']}")
    print(f"보유 재료 사용률: {base['보유 재료 사용률 평균']:.1%} → {cand['보유 재료 사용률 평균']:.1%}")
    print("\n합격률(요리가 실제로 만들어 먹는 것인지 등 5축)은 사람이 채점해야 한다 —")
    print("두 팔의 채점 시트가 같은 폴더에 만들어져 있다.")


if __name__ == "__main__":
    main()
