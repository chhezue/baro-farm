"""과일을 레시피 재료로 허용하면 할루시네이션이 늘어나는가 — 과일 있는 조합과 없는 조합을 같은 프롬프트로 비교한다.

배경: 과일 제외 규칙(`recipe-recommend.yml`의 AGRI_FRUIT* 4종)은 "과일을 넣으면 토마토밥 같은 이상한 음식이
나온다"는 관찰로 들어갔다(사용자 확인 2026-09-07). 1차 측정에서 그 현상은 **옛 프롬프트에서만** 재현됐고
원인은 과일이 아니라 후보 3개를 채우라는 압력이었다. 이 스크립트는 그 뒤 질문에 답한다 —
**제외를 풀면(과일이 보유 재료로 들어오면) 할루시네이션이 늘어나는가.**

재는 것 (전부 서버가 실제로 하는 계산과 같은 규칙):
  - 보유 재료 사용률: 레시피 핵심 재료 중 보유 재료와 일치하는 비율. 낮으면 "가진 걸 두고 딴 요리를 한다"
  - 규칙 2 위반: 보유 재료를 하나도 안 쓴 경우
  - 부족 재료 개수: 새로 사라고 요구하는 재료 수. 많을수록 사실상 다른 요리다
  - 핵심 재료 개수가 3~5인지
요리 이름이 실제로 존재하는지는 기계가 판정할 수 없어 목록을 그대로 출력한다 — 사람이 본다.
"""
import argparse, json, os, sys, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import prompts
from run_goldenset import call_gemini, DEFAULT_MODEL, normalize

# 과일이 보유 재료로 들어오는 조합. 제외를 풀면 실제로 생길 장바구니를 상정했다.
FRUIT = [
    ("P1", ["사과", "계란"], "과일 + 축산"),
    ("P2", ["토마토", "쌀"], "'토마토밥' 재현 조건 — 과일 + 주식"),
    ("P3", ["딸기", "우유"], "디저트·음료가 자연스러운 조합"),
    ("P4", ["감귤", "당근"], "과일 + 뿌리채소"),
    ("P5", ["수박", "돼지고기"], "가장 안 어울리는 조합"),
    ("P6", ["포도", "쌀", "계란"], "과일 + 주식 + 축산"),
    ("P7", ["사과", "양파", "돼지고기"], "과일이 섞인 보통 장바구니"),
    ("P8", ["블루베리", "보리"], "베리류 + 곡류"),
    ("P9", ["배", "무", "돼지고기"], "과일이 양념으로 쓰이는 조합"),
    ("P10", ["감귤", "딸기", "사과"], "과일만 담은 장바구니 — 지금은 레시피가 아예 안 나오는 경우"),
]

# 대조군: 과일이 없는 조합. 지금도 그대로 들어오는 장바구니다.
CONTROL = [
    ("C1", ["계란"], "재료 1개"),
    ("C2", ["돼지고기", "양파", "대파"], "궁합이 좋은 조합"),
    ("C3", ["오이", "쌀", "땅콩"], "궁합이 나쁜 조합"),
    ("C4", ["상추", "깻잎", "치커리"], "샐러드 채소만"),
    ("C5", ["감자", "당근", "무"], "뿌리채소만"),
    ("C6", ["계란", "우유", "삼겹살"], "축산물만"),
    ("C7", ["쌀", "보리"], "곡류만"),
    ("C8", ["호두", "아몬드"], "견과류만"),
    ("C9", ["더덕", "도라지"], "특용작물"),
    ("C10", ["계란", "감자"], "궁합이 애매한 2개"),
]


def parse(resp):
    """Gemini 응답에서 후보 목록을 꺼낸다."""
    try:
        return json.loads(resp["candidates"][0]["content"]["parts"][0]["text"]).get("candidates", [])
    except Exception:
        return []


def evaluate(owned, draft):
    """CandidateRecipePlan.from과 같은 규칙으로 사용·부족 재료를 계산한다."""
    core = [c.strip() for c in (draft.get("recipeIngredientsCore") or []) if c and c.strip()]
    owned_norm = {normalize(o) for o in owned}
    used = [c for c in core if normalize(c) in owned_norm]
    missing = [c for c in core if normalize(c) not in owned_norm]
    return {"name": draft.get("recipeName", ""), "core": core, "used": used, "missing": missing}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--arms", nargs="+", default=["after"],
                    help="비교할 프롬프트 팔. 기본은 현행(after) 하나. "
                         "예시 교체 전후 비교를 재현하려면 --arms fruit-example after")
    ap.add_argument("--groups", nargs="+", default=["fruit", "control"],
                    choices=["fruit", "control"], help="쓸 시나리오 묶음")
    ap.add_argument("--repeats", type=int, default=3)
    ap.add_argument("--delay", type=float, default=4.0)
    ap.add_argument("--model", default=os.environ.get("SPRING_AI_GEMINI_MODEL", DEFAULT_MODEL))
    ap.add_argument("--out", default="../../docs/audits/cart-recipe/goldenset")
    a = ap.parse_args()

    key = os.environ.get("BARO_AI_GEMINI_KEY", "").strip()
    if not key:
        sys.exit("BARO_AI_GEMINI_KEY 환경변수가 비어 있습니다.")

    available = {"fruit": ("과일 포함", FRUIT), "control": ("과일 없음(대조군)", CONTROL)}
    groups = [available[g] for g in a.groups]
    total = sum(len(g) for _, g in groups) * a.repeats * len(a.arms)
    rows, done = [], 0

    for arm in a.arms:
        schema = prompts.ARMS[arm]["schema"]
        for gname, scenarios in groups:
            for sid, owned, intent in scenarios:
                for run in range(1, a.repeats + 1):
                    done += 1
                    drafts = parse(call_gemini(key, a.model, prompts.build_prompt(arm, owned), schema))
                    got = [d for d in drafts if d and (d.get("recipeName") or "").strip()]
                    # before 팔은 후보를 3개 낸다. 지표는 서버가 실제로 고르는 첫 후보로만 계산한다.
                    ev = evaluate(owned, got[0]) if got else None
                    rows.append({"group": gname, "scenario": sid, "run": run, "owned": owned,
                                 "intent": intent, "arm": arm, "result": ev,
                                 "allNames": [d.get("recipeName") for d in got]})
                    label = ev["name"] if ev else "(생성 실패)"
                    print(f"  [{done}/{total}] {arm} {gname} {sid} run{run} ({', '.join(owned)}) -> {label}",
                          flush=True)
                    time.sleep(a.delay)

    out_dir = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), a.out))
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"raw-fruit-{time.strftime('%Y%m%d-%H%M%S')}.jsonl")
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print("\n원본:", path)
    print(f"\n지표 (시나리오당 {a.repeats}회)")
    print(f"  {'팔':14s} {'묶음':18s} {'n':>3s} {'생성':>6s} {'보유재료 사용률':>14s} {'규칙2 위반':>10s} {'부족재료 평균':>13s} {'핵심 3~5':>9s}")
    for arm in a.arms:
      for gname, _ in groups:
        g = [r for r in rows if r["group"] == gname and r["arm"] == arm]
        n = len(g)
        ok = [r for r in g if r["result"]]
        rate = sum(len(r["result"]["used"]) / len(r["owned"]) for r in ok) / len(ok) if ok else 0
        viol = sum(1 for r in ok if not r["result"]["used"])
        miss = sum(len(r["result"]["missing"]) for r in ok) / len(ok) if ok else 0
        core35 = sum(1 for r in ok if 3 <= len(r["result"]["core"]) <= 5)
        print(f"  {arm:14s} {gname:18s} {n:3d} {len(ok):5d}건 {rate*100:13.1f}% "
              f"{viol:9d}건 {miss:12.2f}개 {core35:6d}/{len(ok)}")

    print("\n요리 이름 (사람이 판정할 몫)")
    for arm in a.arms:
        for gname, scenarios in groups:
            print(f"\n  [{arm} · {gname}]")
            for sid, owned, _ in scenarios:
                got = [n for r in rows if r["scenario"] == sid and r["arm"] == arm
                       for n in (r.get("allNames") or [])]
                print(f"    {sid:4s} {str(owned):38s} {got}")


if __name__ == "__main__":
    sys.exit(main())
