#!/usr/bin/env python3
"""골든셋 실행 결과의 '부족 재료'를 판매 가능 여부로 분류해 세 수치를 낸다.

`audits/cart-recipe/002`는 이 분류를 손으로 했고, 그래서 두 팔을 비교할 때 사람 판단이 흔들린다.
같은 목록을 코드로 고정해 두 팔에 똑같이 적용하려고 만든 스크립트다.

분류 근거는 `scripts/init-db/02-init-categories.sql`의 카테고리 21개다 — 과일·채소·곡류·축산물·
견과류·특용작물·지역특산물로 **전부 농산물**이라, 장류·기름·가공식품은 넣을 자리가 없다.

2026-09-06에 김치·두부·장류를 팔기로 정했지만 카테고리가 아직 없어서(PLANNED 참고) 이 목록은
그대로 두고 별도 줄로만 집계한다 — 문서가 실물보다 앞서가지 않게 하려는 것이다.

VARIANT는 원물은 팔지만 요청된 형태로는 상품명 검색이 어긋날 수 있는 것이다(예: "다진마늘"과
"마늘"). 서버의 재료 비교가 완전일치라 이 차이가 실제로 카드 누락으로 이어지는지는
`experiment_variants.py`가 따로 잰다. 그래서 여기서는 엄격/느슨 두 가지로 모두 집계한다.

실행:
    python3 baro-ai/scripts/goldenset/classify_missing.py <raw-*.jsonl> [...]
"""
import json
import sys
from collections import Counter
from pathlib import Path

# 카탈로그 카테고리에 자리가 있는 것 (농산물·축산물)
PRODUCE = {
    "대파", "쪽파", "실파", "양파", "당근", "감자", "고구마", "무", "배추", "상추", "깻잎",
    "치커리", "애호박", "시금치", "오이", "버섯", "표고버섯", "숙주", "콩나물", "나물",
    "마늘", "생강", "고추", "청양고추", "파프리카", "토마토", "사과", "배", "딸기",
    "쌀", "보리", "콩", "팥", "호두", "아몬드", "땅콩", "더덕", "도라지",
    "소고기", "돼지고기", "닭고기", "닭가슴살", "삼겹살", "계란", "우유",
    "서리태", "검은콩", "콩나물", "청양고추", "토마토", "오이", "고구마",
}
# 원물은 팔지만 요청된 형태가 달라 검색이 어긋날 수 있는 것 → 원물 이름
VARIANT = {
    "다진마늘": "마늘",
    "다진 마늘": "마늘",
    "다진파": "대파",
    "대패삼겹살": "삼겹살",
}
# 장류·기름·가루·조미료 — 카탈로그에 자리 없음
SEASONING = {
    "간장", "된장", "고추장", "쌈장", "춘장", "고춧가루", "참기름", "들기름", "식용유",
    "설탕", "소금", "후추", "식초", "액젓", "새우젓", "겨자", "마요네즈", "케첩",
    "밀가루", "부침가루", "튀김가루", "전분", "빵가루", "버터", "올리브유", "카레가루",
    "올리고당", "물엿", "맛술", "미림", "굴소스",
}
# 가공식품·건어물·유제품 — 카탈로그에 자리 없음
PROCESSED = {
    "김치", "열무김치", "묵은지", "두부", "순두부", "유부", "미역", "다시마", "김",
    "멸치", "지리멸치", "북어", "황태", "토란대", "고사리", "당면", "라면", "국수",
    "떡", "떡국떡", "치즈", "베이컨", "햄", "소시지", "어묵", "만두", "빵",
    "잔멸치", "국멸치", "다시멸치", "밥",
}


# 2026-09-06 사용자 결정으로 판매하기로 한 것. 아직 카테고리가 만들어지지 않아 PRODUCE에 넣지 않고
# 따로 둔다 — 지금 합치면 "현재 팔 수 있는 것"이라는 위 목록의 뜻이 흐려진다.
# 카테고리(AGRI_PROCESSED 아래 김치류·두부/콩가공·장류)가 실제로 생기면 PRODUCE로 옮기고 이 상수를 지운다.
PLANNED = {
    "김치", "열무김치", "묵은지", "백김치", "총각김치",
    "두부", "순두부", "유부", "콩비지",
    "고추장", "된장", "간장", "쌈장", "청국장",
}


def classify(name: str) -> str:
    n = "".join(name.split())
    for group, label in ((PRODUCE, "PRODUCE"), (SEASONING, "SEASONING"), (PROCESSED, "PROCESSED")):
        if n in {"".join(x.split()) for x in group}:
            return label
    if n in {"".join(x.split()) for x in VARIANT}:
        return "VARIANT"
    return "UNKNOWN"


def report(path: Path) -> None:
    records = [json.loads(l) for l in path.open(encoding="utf-8")]
    runs = [r for r in records if (r.get("missing_core") is not None and r.get("chosen"))]
    counts = Counter()
    strict_empty = lenient_empty = planned_empty = 0
    unknown = Counter()
    planned_norm = {"".join(x.split()) for x in PLANNED}

    for r in runs:
        labels = [classify(m) for m in r["missing_core"]]
        counts.update(labels)
        for m, lab in zip(r["missing_core"], labels):
            if lab == "UNKNOWN":
                unknown[m] += 1
        # 농산물만 판다고 보면 살 수 있는 게 남는가
        strict = [l for l in labels if l == "PRODUCE"]
        lenient = [l for l in labels if l in ("PRODUCE", "VARIANT")]
        if not strict:
            strict_empty += 1
        if not lenient:
            lenient_empty += 1
        if not lenient and not any("".join(m.split()) in planned_norm for m in r["missing_core"]):
            planned_empty += 1

    total = sum(counts.values())
    print(f"\n=== {path.name} ===")
    print(f"LLM을 부른 실행 {len(runs)}건 / 부족 재료 {total}개")
    for label in ("PRODUCE", "VARIANT", "SEASONING", "PROCESSED", "UNKNOWN"):
        c = counts.get(label, 0)
        if total:
            print(f"  {label:<10} {c:>3}개 ({c / total * 100:4.1f}%)")
    if runs:
        print(f"  살 게 없는 추천 — 엄격(농산물만)     : {strict_empty}/{len(runs)}"
              f" ({strict_empty / len(runs) * 100:.0f}%)")
        print(f"  살 게 없는 추천 — 느슨(변종을 원물로): {lenient_empty}/{len(runs)}"
              f" ({lenient_empty / len(runs) * 100:.0f}%)")
        print(f"  살 게 없는 추천 — 김치·두부·장류까지 : {planned_empty}/{len(runs)}"
              f" ({planned_empty / len(runs) * 100:.0f}%)  ← 2026-09-06 결정, 카테고리 생성 전")
    if unknown:
        print(f"  ⚠ 분류표에 없는 재료: {', '.join(f'{k}×{v}' for k, v in unknown.most_common())}")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    for arg in sys.argv[1:]:
        report(Path(arg))
    return 0


if __name__ == "__main__":
    sys.exit(main())
