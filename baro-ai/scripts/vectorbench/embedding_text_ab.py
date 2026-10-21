"""임베딩 입력 텍스트 A/B — 상품명만 vs 상품명+카테고리명.

현재 `ProductEmbeddingService`는 **상품명만** 임베딩한다. 그래서 이름이 겹치지 않는 같은 종류의 상품
(예: "횡성 한우 등심 300g"과 "제주 흑돼지 삼겹살 500g" — 둘 다 육류인데 공통 어절이 없다)이
서로 멀리 떨어질 수 있다. 카테고리명을 붙이면 그 문제가 풀리는지 재는 도구다.

**판정 규칙은 재기 전에 정한다** (감사 full-module P2-9). 둘을 모두 만족해야 채택한다.

  (1) 이름이 겹치지 않는 동종 상품이 실제로 가까워진다
      — 같은 카테고리이고 상품명에 공통 어절이 없는 쌍의 평균 코사인이 **+0.05 이상** 오른다.
        이보다 작으면 전체 재색인(전 상품 임베딩 재호출)을 정당화하지 못한다.

  (2) 카테고리 안에서 순위를 매길 수 있는 상태가 유지된다
      — 같은 카테고리 쌍 유사도의 표준편차가 **절반 아래로 떨어지지 않는다.**
        모든 항목에 같은 카테고리 문자열을 붙이면 그 성분이 공통으로 들어가 카테고리 안 상품이
        전부 비슷해질 수 있다. 그러면 "그중 무엇을 추천할지"가 사실상 무작위가 된다 —
        추천이 카테고리당 2개만 담는 지금은 그 선택이 곧 결과다.

둘 중 하나라도 어긋나면 기각한다.

사용: eval "$(grep -m1 '^export BARO_AI_GEMINI_KEY' ~/.zshrc)" && python3 embedding_text_ab.py
"""

import argparse
import itertools
import json
import os
import re
import statistics
import time

from common import GEMINI_URL, cosine, gemini_key, http, save_raw

# 운영과 같은 차원(application.yml `dimensions: 768`). 1536으로 재면 운영과 다른 조건이 된다.
DIMS = 768
CATALOG = os.path.join(os.path.dirname(__file__), "..", "localstub", "seeded-products.json")

# 분당 한도에 걸렸을 때 기다리는 시간. 창이 1분이라 넉넉히 잡는다.
RATE_LIMIT_WAIT = 70


def load_catalog():
    with open(CATALOG, encoding="utf-8") as f:
        return json.load(f)


def embed_all(key, texts, pace, batch=5):
    """묶음으로 임베딩한다. 운영 색인 경로와 같은 batchEmbedContents를 쓴다.

    무료 등급은 **분당** 한도가 있어(2026-09-09 실측: 110건을 1분 안에 보내면 429) 429를 만나면
    기다렸다가 같은 묶음을 다시 보낸다. 한도에 걸린 것은 실패가 아니라 속도 문제라서, 그때마다
    측정을 처음부터 다시 하는 것은 낭비다.
    """
    out = {}
    i = 0
    while i < len(texts):
        chunk = texts[i:i + batch]
        body = {"requests": [
            {"model": "models/gemini-embedding-001",
             "content": {"parts": [{"text": t}]},
             "outputDimensionality": DIMS}
            for t in chunk]}
        status, resp, _, _ = http("POST", f"{GEMINI_URL}:batchEmbedContents", body,
                                  headers={"x-goog-api-key": key})
        if status == 429:
            print(f"  분당 한도에 걸려 {RATE_LIMIT_WAIT}초 기다립니다 ({i}/{len(texts)})")
            time.sleep(RATE_LIMIT_WAIT)
            continue
        if status != 200:
            raise SystemExit(f"임베딩 실패 status={status} body={str(resp)[:300]}")
        for t, e in zip(chunk, resp["embeddings"]):
            out[t] = e["values"]
        i += batch
        print(f"  {min(i, len(texts))}/{len(texts)}")
        time.sleep(pace)
    return out


def tokens(name):
    """상품명을 어절로 쪼갠다. 숫자·단위만 있는 조각은 이름이 겹쳤다고 보지 않는다."""
    parts = re.split(r"[\s,()]+", name)
    return {p for p in parts if p and not re.fullmatch(r"[0-9]+[a-zA-Z가-힣]{0,3}", p)}


def analyse(products, vectors, label):
    """한 팔의 지표를 계산한다."""
    same_cat_pairs, diff_cat_pairs, same_cat_no_overlap = [], [], []

    for a, b in itertools.combinations(products, 2):
        sim = cosine(vectors[a["_text"]], vectors[b["_text"]])
        if a["categoryName"] == b["categoryName"]:
            same_cat_pairs.append(sim)
            if not (tokens(a["productName"]) & tokens(b["productName"])):
                same_cat_no_overlap.append(sim)
        else:
            diff_cat_pairs.append(sim)

    # 상위 5개 이웃의 카테고리 순도
    purity = []
    for p in products:
        others = sorted(
            (q for q in products if q is not p),
            key=lambda q: cosine(vectors[p["_text"]], vectors[q["_text"]]),
            reverse=True)[:5]
        purity.append(sum(1 for q in others if q["categoryName"] == p["categoryName"]) / 5)

    return {
        "arm": label,
        "같은 카테고리 쌍 평균": round(statistics.mean(same_cat_pairs), 4),
        "같은 카테고리 쌍 표준편차": round(statistics.pstdev(same_cat_pairs), 4),
        "이름 안 겹치는 동종 쌍 평균": round(statistics.mean(same_cat_no_overlap), 4),
        "이름 안 겹치는 동종 쌍 수": len(same_cat_no_overlap),
        "다른 카테고리 쌍 평균": round(statistics.mean(diff_cat_pairs), 4),
        "동종-이종 평균 차": round(statistics.mean(same_cat_pairs) - statistics.mean(diff_cat_pairs), 4),
        "상위5 카테고리 순도": round(statistics.mean(purity), 4),
    }


def top5(products, vectors):
    return {p["productId"]: [q["productId"] for q in sorted(
        (q for q in products if q is not p),
        key=lambda q: cosine(vectors[p["_text"]], vectors[q["_text"]]),
        reverse=True)[:5]] for p in products}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pace", type=float, default=8.0, help="묶음 사이 대기 초 (무료 등급 분당 한도 회피)")
    ap.add_argument("--out", default="../../docs/audits/vector-similarity/vectorbench")
    args = ap.parse_args()

    key = gemini_key()
    products = load_catalog()
    print(f"상품 {len(products)}개 · 카테고리 {len({p['categoryName'] for p in products})}개")

    arms = {}
    for label, make_text in [
        ("name", lambda p: p["productName"]),
        ("name+category", lambda p: f"{p['productName']} {p['categoryName']}"),
    ]:
        for p in products:
            p["_text"] = make_text(p)
        texts = [p["_text"] for p in products]
        print(f"[{label}] 임베딩 {len(texts)}건")
        vectors = embed_all(key, texts, args.pace)
        arms[label] = {"metrics": analyse(products, vectors, label), "top5": top5(products, vectors)}

    # 두 팔의 상위 5 이웃이 얼마나 달라졌나
    overlaps = []
    for pid, a in arms["name"]["top5"].items():
        b = arms["name+category"]["top5"][pid]
        overlaps.append(len(set(a) & set(b)) / 5)
    churn = round(1 - statistics.mean(overlaps), 4)

    base, cand = arms["name"]["metrics"], arms["name+category"]["metrics"]
    gain = round(cand["이름 안 겹치는 동종 쌍 평균"] - base["이름 안 겹치는 동종 쌍 평균"], 4)
    spread_ratio = round(cand["같은 카테고리 쌍 표준편차"] / base["같은 카테고리 쌍 표준편차"], 4)

    print("\n=== 결과 ===")
    for m in (base, cand):
        print(json.dumps(m, ensure_ascii=False, indent=2))
    print(f"\n상위5 이웃 변화율(1-겹침): {churn}")
    print(f"조건1 — 이름 안 겹치는 동종 쌍 유사도 상승: {gain:+.4f} (기준 +0.05) → {'통과' if gain >= 0.05 else '미달'}")
    print(f"조건2 — 카테고리 안 변별력 유지: 표준편차 비 {spread_ratio} (기준 0.5 초과) → {'통과' if spread_ratio > 0.5 else '미달'}")
    verdict = "채택" if (gain >= 0.05 and spread_ratio > 0.5) else "기각"
    print(f"\n판정: {verdict}")

    save_raw("embedding-text-ab", {
        "dims": DIMS, "products": len(products),
        "name": base, "name+category": cand,
        "top5_churn": churn, "gain": gain, "spread_ratio": spread_ratio, "verdict": verdict,
    }, os.path.join(os.path.dirname(__file__), args.out))


if __name__ == "__main__":
    main()
