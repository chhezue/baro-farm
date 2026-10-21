#!/usr/bin/env python3
"""골든셋 실행 결과의 '부족 재료'를 **카테고리 기준**으로 분류해 "판매 가능 종류가 하나도 없는 추천" 비율을 낸다.

배경 (`audits/cart-recipe/008` §3): 이 저장소에는 판매 상품의 SSOT가 없다. 상품 시드는 어디서도 실행되지
않고, 예전 이 스크립트가 쓰던 `PRODUCE`/`SEASONING` 손 목록은 어떤 실물에도 근거가 없었다. 실제로 DB에
들어가는 유일한 실물은 `scripts/init-db/02-init-categories.sql`의 카테고리 21개(Level 1·2 — 루트 `AGRI_DIRECT`
제외)다(`docker-compose.data.yml`이 MySQL 초기화 경로로 마운트). 그래서 2026-09-09 사용자 결정으로 판정 기준을 다음처럼 바꿨다.

  - 설계(서버 동작)의 SSOT는 상품 검색 인덱스 자체다 — 부족 재료로 검색해 0건이면 카드를 빼는 현행 동작.
    코드나 프롬프트에 "파는 재료 목록"을 따로 두지 않는다.
  - 측정(이 스크립트)의 SSOT는 상품이 0건인 동안 **카테고리 트리**다. 부족 재료마다 "어느 카테고리에
    들어갈 종류인가"를 매핑하고, 매핑되는 카테고리가 실제 SQL에 있어야만 "판매 가능 종류"로 친다.
  - 2026-09-06 사용자 결정으로 팔기로 한 김치·두부·장류는 아직 SQL에 없어 **예정 카테고리**로 따로 센다.

⚠ 이 스크립트가 내는 값은 **종류 수준(카테고리 기준)**이다. "서리태를 파는가" 같은 개별 상품 판정은 상품
시드가 오기 전까지 불가능하고, 그때는 아래 매핑을 상품 인덱스의 `ingredientName`·`productCategoryCode`
집계로 갈아탄다. 재료 → 카테고리 매핑 자체는 사람이 적은 것이지만, 매핑 대상 코드가 SQL에 실재하는지는
실행 때마다 검사한다(없는 코드를 쓰면 즉시 실패).

`[미정]` 둘. (1) 지역 특산물(`AGRI_LOCAL_SPECIALTY`)에는 어떤 재료도 매핑하지 않았다 — 순창 고추장처럼 가공품이
여기 들어올 수 있어 해석이 열려 있다. (2) 참깨(`통깨`·`깨`)는 농산물 분류상 특용작물이지만 SQL의 Level 2가
`약용/기능성 작물` 하나뿐이라 일단 NO_SLOT에 뒀다. 둘 다 사용자 확인 후 채운다.

"나물"·"고기"처럼 종류를 특정하지 않는 총칭은 매핑하지 않는다 — UNKNOWN으로 드러나게 둔다.

VARIANT는 원물의 종류는 분명하지만 요청된 형태가 다른 것(예: "다진마늘"→"마늘")이다. 그래서 "판매 가능 종류가
없는 추천"을 엄격(실카테고리만)·느슨(형태 다름 포함)·예정 카테고리 포함, 세 값으로 낸다. 부족 재료가 0개인
실행은 "팔 게 없어서 실패한 추천"이 아니므로 이 세 값의 분모에서 빼고 따로 센다.

실행:
    python3 baro-ai/scripts/goldenset/classify_missing.py <raw-*.jsonl> [...]
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

CATEGORY_SQL = Path(__file__).resolve().parents[3] / "scripts" / "init-db" / "02-init-categories.sql"

# ── 실카테고리 (Level 1). 코드는 실행 시 SQL과 대조한다 ──────────────────────────────────────────
FRUIT, VEG, GRAIN, SPECIAL, NUT, LIVESTOCK = (
    "AGRI_FRUIT", "AGRI_VEGETABLE", "AGRI_GRAIN", "AGRI_SPECIAL", "AGRI_NUT", "AGRI_LIVESTOCK",
)
# ── 예정 카테고리 (2026-09-06 사용자 결정, SQL 미반영 — 스펙 pantry-staples-and-catalog-coverage.md 갈래 B) ──
KIMCHI, SOY, JANG = "AGRI_PROCESSED_KIMCHI", "AGRI_PROCESSED_SOY", "AGRI_PROCESSED_JANG"
PLANNED_CODES = {KIMCHI, SOY, JANG}

# 재료 → 카테고리 코드. "이 종류가 들어갈 칸이 카테고리 트리에 있는가"만 답한다.
INGREDIENT_CATEGORY = {
    # 채소
    **dict.fromkeys(["대파", "쪽파", "실파", "부추", "양파", "당근", "감자", "고구마", "무", "배추", "양배추",
                     "상추", "깻잎", "치커리", "애호박", "시금치", "오이", "숙주", "숙주나물", "콩나물",
                     "마늘", "생강", "고추", "청양고추", "피망", "파프리카", "토마토", "고사리", "토란대"], VEG),
    # 과일
    **dict.fromkeys(["사과", "배", "딸기"], FRUIT),
    # 곡류·잡곡
    **dict.fromkeys(["쌀", "보리", "콩", "대두", "팥", "서리태", "검은콩"], GRAIN),
    # 버섯·특용작물
    **dict.fromkeys(["버섯", "표고버섯", "더덕", "도라지", "인삼"], SPECIAL),
    # 견과류
    **dict.fromkeys(["호두", "아몬드", "땅콩"], NUT),
    # 축산물 (계란·육류·유제품)
    **dict.fromkeys(["소고기", "쇠고기", "돼지고기", "삼겹살", "닭고기", "닭가슴살", "계란", "달걀", "우유"], LIVESTOCK),
    # 예정 — 김치류 / 두부·콩가공 / 장류
    **dict.fromkeys(["김치", "열무김치", "묵은지", "백김치", "총각김치"], KIMCHI),
    **dict.fromkeys(["두부", "순두부", "유부", "콩비지"], SOY),
    **dict.fromkeys(["고추장", "된장", "간장", "쌈장", "청국장"], JANG),
}
# 원물 종류는 분명하지만 형태가 다른 것 → 원물 이름
VARIANT = {"다진마늘": "마늘", "다진파": "대파", "대패삼겹살": "삼겹살"}
# 실재하는 식재료지만 카테고리 트리에 Level 1 칸이 없는 것 (양념·기름·가루·수산물·가공식품). 통깨·깨는 [미정] — docstring
NO_SLOT = {
    "고춧가루", "고추가루", "참기름", "들기름", "식용유", "올리브유", "설탕", "소금", "후추", "식초", "액젓",
    "멸치액젓", "새우젓", "겨자", "마요네즈", "케첩", "굴소스", "맛술", "미림", "올리고당", "물엿", "꿀", "시럽",
    "통깨", "깨", "밀가루", "박력분", "부침가루", "튀김가루", "전분", "빵가루", "카레가루", "버터", "치즈",
    "멸치", "지리멸치", "잔멸치", "국멸치", "다시멸치", "북어", "황태", "오징어", "미역", "다시마", "김", "맛살",
    "라면", "국수", "당면", "떡", "떡국떡", "식빵", "빵", "타르트지", "베이컨", "햄", "소시지", "스팸", "어묵", "만두",
    "해물믹스",
}
# 상품으로 존재하지 않는 것 — 물이거나 집에서 만드는 것
NOT_A_PRODUCT = {"물", "찹쌀풀", "밥"}


def load_sql_codes(path: Path = CATEGORY_SQL) -> set[str]:
    """SQL에서 실제로 INSERT되는 카테고리 코드를 읽는다. 매핑이 가리키는 코드는 전부 여기 있어야 한다."""
    lines = [l for l in path.read_text(encoding="utf-8").splitlines() if not l.lstrip().startswith("--")]
    return set(re.findall(r"'(AGRI_[A-Z_]+)'", "\n".join(lines)))


def verify_mapping(sql_codes: set[str]) -> None:
    used = set(INGREDIENT_CATEGORY.values())
    missing = used - sql_codes - PLANNED_CODES
    if missing:
        sys.exit(f"매핑이 SQL에 없는 카테고리 코드를 가리킨다: {sorted(missing)} ({CATEGORY_SQL})")
    bad_variant = {v for v in VARIANT.values() if INGREDIENT_CATEGORY.get(v) is None or INGREDIENT_CATEGORY[v] in PLANNED_CODES}
    if bad_variant:
        sys.exit(f"VARIANT의 원물이 실카테고리 매핑에 없다: {sorted(bad_variant)}")
    leaked = PLANNED_CODES & sql_codes
    if leaked:
        print(f"ℹ 예정 카테고리가 SQL에 생겼다: {sorted(leaked)} — PLANNED_CODES에서 빼고 실카테고리로 취급할 것")


def _norm(s: str) -> str:
    return "".join(s.split())


def classify(name: str) -> tuple[str, str | None]:
    """(라벨, 카테고리 코드). 라벨: REAL / PLANNED / VARIANT / NO_SLOT / NOT_A_PRODUCT / UNKNOWN."""
    n = _norm(name)
    if n in VARIANT:
        return "VARIANT", INGREDIENT_CATEGORY.get(VARIANT[n])
    code = INGREDIENT_CATEGORY.get(n)
    if code:
        return ("PLANNED" if code in PLANNED_CODES else "REAL"), code
    if n in NO_SLOT:
        return "NO_SLOT", None
    if n in NOT_A_PRODUCT:
        return "NOT_A_PRODUCT", None
    return "UNKNOWN", None


def report(path: Path) -> None:
    records = [json.loads(l) for l in path.open(encoding="utf-8") if l.strip()]
    chosen = [r for r in records if (r.get("missing_core") is not None and r.get("chosen"))]
    no_missing = sum(1 for r in chosen if not r["missing_core"])
    runs = [r for r in chosen if r["missing_core"]]
    labels = Counter()
    by_code = Counter()
    unknown = Counter()
    strict_empty = lenient_empty = planned_empty = 0

    for r in runs:
        got = [classify(m) for m in r["missing_core"]]
        labels.update(lab for lab, _ in got)
        by_code.update(code for _, code in got if code)
        for m, (lab, _) in zip(r["missing_core"], got):
            if lab == "UNKNOWN":
                unknown[m] += 1
        strict = any(lab == "REAL" for lab, _ in got)
        lenient = strict or any(lab == "VARIANT" for lab, _ in got)
        planned = lenient or any(lab == "PLANNED" for lab, _ in got)
        strict_empty += not strict
        lenient_empty += not lenient
        planned_empty += not planned

    total = sum(labels.values())
    n = len(runs)
    print(f"\n=== {path.name} ===")
    print(f"부족 재료가 있는 추천 {n}건 / 부족 재료 {total}개" + (f" (부족 재료 0개 추천 {no_missing}건은 아래 비율의 분모에서 제외)" if no_missing else "")),
    print("  ⚠ 종류 수준(카테고리 기준) 판정 — 개별 상품의 판매 여부가 아니다 (audits/cart-recipe/008 §3)")
    for label, desc in (("REAL", "실카테고리에 종류가 있음"), ("VARIANT", "종류는 있으나 형태가 다름"),
                        ("PLANNED", "예정 카테고리(김치·두부·장류)"), ("NO_SLOT", "카테고리에 칸이 없음"),
                        ("NOT_A_PRODUCT", "상품이 아님"), ("UNKNOWN", "매핑 없음")):
        c = labels.get(label, 0)
        if total:
            print(f"  {label:<14} {c:>3}개 ({c / total * 100:4.1f}%)  {desc}")
    if by_code:
        print("  카테고리별(REAL+VARIANT+PLANNED 합산): " + ", ".join(f"{k}×{v}" for k, v in by_code.most_common()))
    if n:
        print(f"  판매 가능 종류가 없는 추천 — 엄격(실카테고리만)   : {strict_empty}/{n} ({strict_empty / n * 100:.0f}%)")
        print(f"  판매 가능 종류가 없는 추천 — 느슨(형태 다름 포함) : {lenient_empty}/{n} ({lenient_empty / n * 100:.0f}%)")
        print(f"  판매 가능 종류가 없는 추천 — 예정 카테고리까지     : {planned_empty}/{n} ({planned_empty / n * 100:.0f}%)")
    if unknown:
        print(f"  ⚠ 매핑에 없는 재료: {', '.join(f'{k}×{v}' for k, v in unknown.most_common())}")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    verify_mapping(load_sql_codes())
    for arg in sys.argv[1:]:
        report(Path(arg))
    return 0


if __name__ == "__main__":
    sys.exit(main())
