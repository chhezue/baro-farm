"""제외를 없애면 개인화 추천이 "방금 산 것 목록"이 되는지 재는 시뮬레이션.

개인화 추천의 기준 벡터는 **최근에 담거나 산 상품들의 가중 평균**이다. 그래서 그 평균에
가장 가까운 상품이 무엇이냐고 물으면, 평균을 만든 상품들 자신이 상위에 올라오는 것이 자연스럽다.
지금 코드가 그 상품들을 결과에서 빼는 이유가 그것이라면, 빼기를 그만두는 순간 추천 칸이
"당신이 방금 산 것 5개"가 된다.

**그 추측이 실제로 맞는지**를 재려고 만들었다. 실제 API를 부르지 않고, 이미 캐시된 상품명 임베딩으로
같은 계산(가중 평균 → L2 정규화 → 코사인 정렬 → 카테고리 상한 2개)을 재현한다. 외부 호출 0회다.

캐시는 1536차원(운영은 768차원)이지만 여기서 묻는 것은 절대 유사도가 아니라 **순위 구조**
("자기 자신이 상위에 오는가")라서 차원 차이가 결론을 바꾸지 않는다.

사용: python3 repurchase_exclusion_sim.py [--trials 300]
"""

import argparse
import json
import math
import os
import random
import statistics

from diversity import CATALOG

CACHE = os.path.join(os.path.dirname(__file__), "embeddings-cache.json")

# 운영 코드와 같은 값 (UserProfileEmbeddingService · VectorRecommendPipeline)
ORDER_CONFIRMED, CART_ITEM_ADDED, CART_QUANTITY_UPDATED = 3, 2, 1
RECENT_LOG_LIMIT = 10
MAX_PER_CATEGORY = 2
TOP_K = 5


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def taste_vector(basket, vectors):
    """운영과 같은 방식: 행동 가중치로 더한 뒤 길이를 1로 맞춘다."""
    dims = len(next(iter(vectors.values())))
    acc = [0.0] * dims
    for name, weight in basket:
        v = vectors[name]
        for i in range(dims):
            acc[i] += weight * v[i]
    norm = math.sqrt(sum(x * x for x in acc))
    return [x / norm for x in acc] if norm else acc


def select_with_category_spread(ranked, category_of, top_k=TOP_K):
    """VectorRecommendPipeline.selectWithCategorySpread와 같은 규칙."""
    selected, per_cat, picked = [], {}, set()
    for name in ranked:
        if len(selected) >= top_k:
            break
        cat = category_of[name]
        if per_cat.get(cat, 0) >= MAX_PER_CATEGORY:
            continue
        per_cat[cat] = per_cat.get(cat, 0) + 1
        picked.add(name)
        selected.append(name)
    for name in ranked:
        if len(selected) >= top_k:
            break
        if name not in picked:
            selected.append(name)
    return selected


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--trials", type=int, default=300)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    with open(CACHE, encoding="utf-8") as f:
        vectors = json.load(f)

    category_of = {n: cat for cat, ns in CATALOG.items() for n in ns if n in vectors}
    products = sorted(category_of)
    rng = random.Random(args.seed)

    print(f"상품 {len(products)}개 · 카테고리 {len(set(category_of.values()))}개 · 시도 {args.trials}회\n")

    self_hits_without, overlap_without, overlap_with = [], [], []
    example = None

    for t in range(args.trials):
        # 최근 행동 10건을 흉내 낸다 — 한 번 장을 보면 여러 개를 담고 일부는 주문까지 간다
        basket_names = rng.sample(products, rng.randint(3, RECENT_LOG_LIMIT))
        basket = [(n, rng.choice([ORDER_CONFIRMED, CART_ITEM_ADDED, CART_QUANTITY_UPDATED]))
                  for n in basket_names]
        experienced = {n for n, _ in basket}

        query = taste_vector(basket, vectors)
        ranked_all = sorted(products, key=lambda n: cosine(query, vectors[n]), reverse=True)

        without = select_with_category_spread(ranked_all, category_of)
        with_excl = select_with_category_spread(
            [n for n in ranked_all if n not in experienced], category_of)

        hits = sum(1 for n in without if n in experienced)
        self_hits_without.append(hits / TOP_K)
        overlap_without.append(hits)
        overlap_with.append(sum(1 for n in with_excl if n in experienced))

        if example is None and hits >= 3:
            example = (basket_names, without, with_excl)

    print("=== 제외를 없애면 추천 5개 중 몇 개가 '방금 담은 것'인가 ===")
    print(f"평균 비율      : {statistics.mean(self_hits_without):.1%}")
    print(f"평균 개수      : {statistics.mean(overlap_without):.2f} / 5")
    print(f"5개 전부인 경우: {sum(1 for x in overlap_without if x == TOP_K) / len(overlap_without):.1%}")
    print(f"3개 이상인 경우: {sum(1 for x in overlap_without if x >= 3) / len(overlap_without):.1%}")
    print(f"0개인 경우     : {sum(1 for x in overlap_without if x == 0) / len(overlap_without):.1%}")
    print(f"\n(현행 제외 적용 시 겹침: 평균 {statistics.mean(overlap_with):.2f} — 0이어야 정상)")

    if example:
        basket_names, without, with_excl = example
        print("\n=== 예시 한 건 ===")
        print(f"최근 담은 것 : {', '.join(basket_names)}")
        print(f"제외 없음    : {', '.join(without)}")
        print(f"현행(제외)   : {', '.join(with_excl)}")


if __name__ == "__main__":
    main()
