"""M-2 — Max-Min 다양화를 켰을 때와 껐을 때의 추천 결과 차이를 숫자로 잰다.

현실적인 상품명(카테고리별)을 Gemini `gemini-embedding-001`(1536차원)로 임베딩해 로컬 파일에 캐시하고,
기준 상품마다 두 팔을 비교한다:
  OFF — 코사인 유사도 상위 k 그대로 (다양화 없음)
  ON  — 후보 k×multiplier(상한 cap) → Java `MaxMinDiversityService.selectDiverse`와 같은 절차 → 유사도순 재정렬
지표: (1) 선택된 k개의 평균 상호 코사인 거리 (2) 같은 카테고리 쌍 비율 (3) 기준 상품과의 평균 유사도(대가).
Java 구현과 같은 답을 내는지는 `--selfcheck`가 `MaxMinDiversityServiceTest`와 같은 3차원 예제로 확인한다.

Gemini 무료 티어는 분당 요청 한도가 빠듯하다(2026-09-06 실측: 단건 13회 뒤 429). 배치 10개씩, 호출 사이 `--pace`초 쉰다.
임베딩은 `embeddings-cache.json`에 저장되어 두 번째 실행부터는 API를 부르지 않는다.

사용: eval "$(grep -m1 '^export BARO_AI_GEMINI_KEY' ~/.zshrc)" && python3 diversity.py [--k 5 10] [--multiplier 2 3 5] [--pace 6]
"""

import argparse
import json
import math
import os
import time

from common import GEMINI_URL, cosine, gemini_key, http, save_raw, seeded

# 카테고리 코드는 scripts/init-db/02-init-categories.sql의 하위 카테고리를 따랐다. 상품명은 벤치마크용으로 지어냈다.
CATALOG = {
    "AGRI_FRUIT_SEASONAL": ["제주 서귀포 고당도 노지 감귤 3kg", "논산 딸기 설향 500g", "성주 참외 2kg", "경산 대추 1kg", "천안 거봉 포도 2kg",
                            "무안 수박 1통 8kg", "김천 자두 1.5kg", "영천 복숭아 백도 2kg", "밀양 얼음골 사과 3kg", "제주 한라봉 3kg",
                            "완도 유자 2kg", "나주 신고배 3kg"],
    "AGRI_FRUIT_STORAGE": ["청송 꿀사과 부사 5kg 가정용", "충주 사과 홍로 3kg", "상주 곶감 반건시 1kg", "국내산 깐 밤 500g", "영주 부사 사과 10kg 박스",
                           "국내산 단감 3kg", "장수 사과 아오리 2kg", "경북 건포도 500g", "국산 곶감 선물세트", "문경 오미자 1kg"],
    "AGRI_FRUIT_BERRY": ["국산 블루베리 500g", "국산 산딸기 300g", "고창 복분자 1kg", "국산 오디 500g", "국산 아로니아 1kg",
                         "논산 딸기 죽향 500g", "국산 크랜베리 300g", "국산 블랙베리 300g", "제주 블루베리 1kg", "국산 구기자 500g"],
    "AGRI_VEG_SALAD": ["국내산 상추 200g 청상추", "국내산 깻잎 100g", "국산 로메인 1포기", "국산 루꼴라 100g", "국산 어린잎 채소 150g",
                       "국산 양상추 1통", "국산 치커리 200g", "국산 케일 200g", "국내산 방울토마토 1kg 대추방울", "국산 오이 3개",
                       "국내산 파프리카 빨강 2개", "국산 샐러리 1단"],
    "AGRI_VEG_SOUP": ["국내산 애호박 1개", "국산 무 1개", "국산 배추 1포기", "진도 대파 1단", "국산 콩나물 300g",
                      "국산 두부 300g 부침용", "국산 미나리 200g", "국산 시금치 300g", "국산 알배추 1통", "국산 우거지 500g",
                      "국산 숙주나물 300g", "국산 청경채 300g"],
    "AGRI_VEG_STIRFRY": ["강원 고랭지 양배추 1통", "국내산 가지 3개", "국내산 브로콜리 1송이", "국산 부추 300g", "국산 쪽파 300g",
                         "국산 피망 3개", "국산 호박 1개 단호박", "국산 양파 3kg 햇양파", "국산 꽈리고추 200g", "국산 청양고추 200g",
                         "국산 마늘쫑 300g", "국산 아스파라거스 200g"],
    "AGRI_VEG_ROOT": ["해남 세척 당근 1kg", "국내산 감자 3kg 수미", "해남 꿀고구마 3kg 베니하루카", "의성 마늘 깐마늘 500g", "안동 생강 500g",
                      "국산 연근 500g", "국산 우엉 500g", "국산 비트 1kg", "국산 알감자 1kg", "국산 자색고구마 2kg", "국산 토란 500g", "국산 더덕 500g"],
    "AGRI_GRAIN_STAPLE": ["이천 쌀 10kg 햅쌀", "국산 현미 5kg", "국산 찹쌀 2kg", "국산 흑미 1kg", "국산 보리쌀 2kg",
                          "국산 귀리 1kg", "국산 수수 1kg", "국산 기장 1kg", "국산 율무 1kg", "국산 옥수수 5개 찰옥수수", "제주 초당옥수수 5개", "국산 서리태 1kg"],
    "AGRI_SPECIAL_MEDICINAL": ["국산 표고버섯 300g", "새송이버섯 400g", "국내산 팽이버섯 150g 3봉", "국산 느타리버섯 300g", "국산 양송이 300g",
                               "국산 도라지 500g", "국산 인삼 300g 4년근", "국산 황기 300g", "국산 당귀 200g", "국산 능이버섯 300g", "국산 목이버섯 200g", "국산 상황버섯 100g"],
    "AGRI_NUT_RAW": ["국산 호두 500g", "국산 땅콩 1kg", "국산 잣 300g", "국산 은행 500g", "국산 아몬드 500g", "국산 해바라기씨 300g",
                     "국산 호박씨 300g", "국산 캐슈넛 300g", "국산 피스타치오 300g", "국산 마카다미아 300g"],
    "AGRI_LOCAL_REPRESENT": ["청양 고춧가루 1kg", "경북 영양 건고추 500g", "순창 재래식 된장 1kg", "순창 고추장 1kg 태양초", "국산 청국장 500g",
                             "종가집 포기김치 3kg", "국산 총각김치 1kg", "국산 콩비지 300g", "국산 참기름 300ml", "국산 들기름 300ml", "보성 녹차 100g", "담양 죽순 500g"],
    "AGRI_LIVESTOCK_EGG": ["곰곰 무항생제 신선한 대란 30구", "국산 유정란 10구", "국산 메추리알 30구", "국산 특란 15구", "동물복지 계란 10구",
                           "국산 오리알 10구", "국산 왕란 20구", "국산 청란 10구"],
    "AGRI_LIVESTOCK_MEAT": ["횡성 한우 등심 300g 1등급", "제주 흑돼지 삼겹살 500g", "국산 닭가슴살 1kg", "국산 돼지 목살 500g", "국산 한우 국거리 500g",
                            "국산 닭 한마리 1kg", "국산 돼지 앞다리살 1kg", "국산 오리 훈제 500g", "국산 한우 양지 500g", "국산 소 사골 2kg", "국산 돼지 갈비 1kg", "국산 닭 다리살 1kg"],
    "AGRI_LIVESTOCK_DAIRY": ["국산 우유 1L", "국산 요거트 450g", "국산 치즈 200g", "국산 버터 200g", "국산 생크림 500ml",
                             "국산 산양유 500ml", "국산 치즈 스트링 100g", "국산 발효유 750ml"],
}

CACHE = os.path.join(os.path.dirname(__file__), "embeddings-cache.json")


# ---- Java MaxMinDiversityService.selectDiverse 를 그대로 옮긴 것 ----

def select_diverse(query_vec, candidates, top_k):
    """candidates: [(id, vec, category)] 유사도 내림차순. Java와 같은 절차: 첫 후보 고정 → 최소 거리 갱신 → 최대 선택 → 유사도순 재정렬."""
    valid = [c for c in candidates if c[1]]
    if top_k <= 0 or not valid:
        return []
    k = min(top_k, len(valid))
    if len(valid) <= k:
        return valid[:k]
    selected = [valid[0]]
    picked = [False] * len(valid)
    picked[0] = True
    min_dist = [math.inf] * len(valid)
    while len(selected) < k:
        next_i = -1
        max_min = -1.0
        last = selected[-1]
        for i, c in enumerate(valid):
            if picked[i]:
                continue
            sim = cosine(c[1], last[1])
            if math.isnan(sim):
                continue
            d = 1.0 - sim
            min_dist[i] = min(min_dist[i], d)
            if min_dist[i] > max_min:
                max_min = min_dist[i]
                next_i = i
        if next_i < 0:
            break
        picked[next_i] = True
        selected.append(valid[next_i])
    if query_vec is not None:
        selected.sort(key=lambda c: -cosine(c[1], query_vec))
    return selected


def selfcheck():
    """MaxMinDiversityServiceTest와 같은 예제 — Java와 같은 답이어야 한다."""
    q = [1.0, 0.0, 0.0]
    cands = [
        ("a", [1.0, 0.0, 0.0], "x"),   # 기준과 동일 → 첫 고정
        ("b", [0.9, 0.1, 0.0], "x"),   # a와 매우 비슷
        ("c", [0.0, 1.0, 0.0], "y"),   # a와 직교
        ("d", [0.0, 0.0, 1.0], "z"),   # a·c 모두와 직교
        ("e", [0.8, 0.2, 0.0], "x"),
    ]
    got = [c[0] for c in select_diverse(q, cands, 3)]
    expected = ["a", "c", "d"]
    assert got == expected, (got, expected)
    print("selfcheck ok:", got)


# ---- 임베딩 ----

def load_cache():
    if os.path.exists(CACHE):
        with open(CACHE) as f:
            return json.load(f)
    return {}


def embed_missing(names, pace, batch=10):
    cache = load_cache()
    todo = [n for n in names if n not in cache]
    if not todo:
        return cache
    key = gemini_key()
    print(f"embedding {len(todo)} names in batches of {batch} (pace {pace}s)")
    for start in range(0, len(todo), batch):
        chunk = todo[start:start + batch]
        body = {"requests": [
            {"model": "models/gemini-embedding-001", "content": {"parts": [{"text": t}]}, "outputDimensionality": 1536}
            for t in chunk]}
        status, resp, _, _ = http("POST", f"{GEMINI_URL}:batchEmbedContents", body, headers={"x-goog-api-key": key})
        if status != 200:
            with open(CACHE, "w") as f:
                json.dump(cache, f)
            raise SystemExit(f"Gemini {status}: {str(resp)[:200]} — 캐시 {len(cache)}건 저장, 나중에 다시 실행하면 이어서 한다")
        for t, e in zip(chunk, resp["embeddings"]):
            cache[t] = e["values"]
        with open(CACHE, "w") as f:
            json.dump(cache, f)
        print(f"  {min(start + batch, len(todo))}/{len(todo)}", flush=True)
        time.sleep(pace)
    return cache


# ---- 지표 ----

def mean_pairwise_distance(items):
    if len(items) < 2:
        return float("nan")
    ds = []
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            ds.append(1.0 - cosine(items[i][1], items[j][1]))
    return sum(ds) / len(ds)


def same_category_ratio(items):
    if len(items) < 2:
        return float("nan")
    pairs = same = 0
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            pairs += 1
            same += items[i][2] == items[j][2]
    return same / pairs


def mean_similarity(query_vec, items):
    return sum(cosine(query_vec, c[1]) for c in items) / len(items)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--k", type=int, nargs="+", default=[5, 10])
    ap.add_argument("--multiplier", type=int, nargs="+", default=[2, 3, 5])
    ap.add_argument("--cap", type=int, default=100)
    ap.add_argument("--queries", type=int, default=20)
    ap.add_argument("--pace", type=float, default=6.0)
    ap.add_argument("--selfcheck", action="store_true")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "..", "docs", "audits", "vector-similarity", "vectorbench"))
    args = ap.parse_args()

    selfcheck()
    if args.selfcheck:
        return

    names = [(n, cat) for cat, ns in CATALOG.items() for n in ns]
    cache = embed_missing([n for n, _ in names], args.pace)
    products = [(n, cache[n], cat) for n, cat in names]
    print(f"products: {len(products)}, categories: {len(CATALOG)}")

    rng = seeded()
    query_idx = rng.sample(range(len(products)), min(args.queries, len(products)))
    result = {"products": len(products), "categories": len(CATALOG), "queries": len(query_idx), "runs": []}

    for k in args.k:
        for mult in [None] + args.multiplier:
            dist, dup, sim, cand_sizes = [], [], [], []
            for qi in query_idx:
                q = products[qi]
                ranked = sorted((p for p in products if p[0] != q[0]), key=lambda p: -cosine(p[1], q[1]))
                if mult is None:
                    chosen = ranked[:k]
                else:
                    cand = ranked[:min(k * mult, args.cap)]
                    cand_sizes.append(len(cand))
                    chosen = select_diverse(q[1], cand, k)
                dist.append(mean_pairwise_distance(chosen))
                dup.append(same_category_ratio(chosen))
                sim.append(mean_similarity(q[1], chosen))
            run = {
                "k": k, "arm": "OFF (top-k)" if mult is None else f"ON x{mult}",
                "candidates": None if mult is None else round(sum(cand_sizes) / len(cand_sizes), 1),
                "mean_pairwise_distance": round(sum(dist) / len(dist), 4),
                "same_category_pair_ratio": round(sum(dup) / len(dup), 4),
                "mean_similarity_to_query": round(sum(sim) / len(sim), 4),
            }
            result["runs"].append(run)
            print(run)

    print("raw:", save_raw("diversity", result, args.out))


if __name__ == "__main__":
    main()
