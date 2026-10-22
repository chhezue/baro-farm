"""M-1 — `script_score`(현재 운영 쿼리, 선형 스캔) vs 최상위 `knn`(HNSW) 지연·일치율을 상품 수별로 잰다.

인덱스는 운영 `product`와 같은 조건으로 만든다: `dense_vector` dims=1536, `index`·`similarity`는 서버 기본값
(8.12: index=true, cosine — Spring Data가 보내는 매핑도 `{"type":"dense_vector","dims":1536}`뿐이다. 감사 vector-similarity/001 C-1).
두 쿼리 모두 운영과 같은 필터(status ∈ {ON_SALE, DISCOUNTED} + vector exists)를 건다. 벡터 값은 난수다 — 지연은 문서 수·차원·
인덱스 구조에 의존하므로 충분하고, 일치율만 실데이터와 다를 수 있어 참고값으로 표시한다.

사용: python3 knn_bench.py --docs 1000 10000 [--queries 200] [--k 30] [--keep]
"""

import argparse
import json
import os
import time

from common import DIMS, es, ms, percentiles, random_unit_vector, save_raw, seeded

FILTER = {"bool": {"filter": [
    {"terms": {"status": ["ON_SALE", "DISCOUNTED"]}},
    {"exists": {"field": "vector"}},
]}}


def index_name(n):
    return f"bench_vec_{n}"


def mapping():
    return {
        "settings": {"number_of_shards": 1, "number_of_replicas": 0, "refresh_interval": "-1"},
        "mappings": {"properties": {
            "productName": {"type": "keyword"},
            "productCategoryCode": {"type": "keyword"},
            "status": {"type": "keyword"},
            "vector": {"type": "dense_vector", "dims": DIMS},  # index/similarity는 서버 기본값 — 운영 매핑과 동일
        }},
    }


def seed(n, rng, bulk=500):
    name = index_name(n)
    es("DELETE", f"/{name}")
    status, body, _, _ = es("PUT", f"/{name}", mapping())
    assert status == 200, body
    t0 = time.perf_counter()
    for start in range(0, n, bulk):
        lines = []
        for i in range(start, min(start + bulk, n)):
            lines.append(json.dumps({"index": {"_index": name, "_id": str(i)}}))
            # 5%는 SOLD_OUT — 필터가 실제로 일하게
            status_v = "SOLD_OUT" if i % 20 == 0 else ("DISCOUNTED" if i % 7 == 0 else "ON_SALE")
            lines.append(json.dumps({"productName": f"p{i}", "productCategoryCode": f"C{i % 22}", "status": status_v, "vector": random_unit_vector(rng)}))
        status, body, _, _ = es("POST", "/_bulk", "\n".join(lines) + "\n")
        assert status == 200 and not body.get("errors"), str(body)[:300]
        if (start // bulk) % 20 == 0:
            print(f"  seeded {min(start + bulk, n)}/{n}", flush=True)
    es("POST", f"/{name}/_refresh")
    es("POST", f"/{name}/_forcemerge?max_num_segments=1", timeout=1800)
    es("POST", f"/{name}/_refresh")
    return time.perf_counter() - t0


def q_script(vec, k):
    return {"size": k, "_source": False, "query": {"script_score": {
        "query": FILTER,
        "script": {"source": "cosineSimilarity(params.query_vector, 'vector') + 1.0", "params": {"query_vector": vec}},
    }}}


def q_knn(vec, k, num_candidates):
    return {"size": k, "_source": False, "knn": {
        "field": "vector", "query_vector": vec, "k": k, "num_candidates": num_candidates, "filter": FILTER,
    }}


def run_arm(name, build, queries, k):
    wall, took, id_lists = [], [], []
    for vec in queries:
        status, body, elapsed, _ = es("POST", f"/{name}/_search", build(vec, k))
        assert status == 200, str(body)[:300]
        wall.append(elapsed)
        took.append(body["took"] / 1000)
        id_lists.append([h["_id"] for h in body["hits"]["hits"]])
    return {"wall_ms": percentiles([ms(x) for x in wall]), "took_ms": percentiles([ms(x) for x in took])}, id_lists


def overlap(a_lists, b_lists, k):
    ratios = [len(set(a) & set(b)) / k for a, b in zip(a_lists, b_lists)]
    return {"mean": round(sum(ratios) / len(ratios), 4), "min": round(min(ratios), 4)}


def source_bytes(name, vec, k):
    """A-1 근거: 같은 k건을 벡터 포함 _source로 받을 때와 _source 없이 받을 때의 응답 바이트."""
    with_src = dict(q_script(vec, k)); with_src["_source"] = True
    _, _, _, b_with = es("POST", f"/{name}/_search", with_src)
    _, _, _, b_without = es("POST", f"/{name}/_search", q_script(vec, k))
    return {"k": k, "with_vector_bytes": b_with, "without_vector_bytes": b_without}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--docs", type=int, nargs="+", default=[1000, 10000])
    ap.add_argument("--queries", type=int, default=200)
    ap.add_argument("--k", type=int, default=30)
    ap.add_argument("--keep", action="store_true", help="측정 후 인덱스를 지우지 않는다")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "..", "docs", "audits", "vector-similarity", "vectorbench"))
    args = ap.parse_args()

    rng = seeded()
    queries = [random_unit_vector(rng) for _ in range(args.queries)]
    warm = queries[:10]
    es_version = es("GET", "/")[1]["version"]["number"]
    results = {"es_version": es_version, "dims": DIMS, "k": args.k, "queries": args.queries, "runs": []}

    for n in args.docs:
        name = index_name(n)
        print(f"== {n} docs ==", flush=True)
        seed_sec = seed(n, rng)
        mapping_now = es("GET", f"/{name}/_mapping")[1][name]["mappings"]["properties"]["vector"]
        for build in (q_script, q_knn if False else None):
            pass
        # 워밍업(페이지 캐시·JIT) 뒤 측정
        for vec in warm:
            es("POST", f"/{name}/_search", q_script(vec, args.k))
            es("POST", f"/{name}/_search", q_knn(vec, args.k, args.k * 2))
        script_stats, script_ids = run_arm(name, q_script, queries, args.k)
        knn_stats, knn_ids = run_arm(name, lambda v, k: q_knn(v, k, k * 2), queries, args.k)
        knn5_stats, knn5_ids = run_arm(name, lambda v, k: q_knn(v, k, k * 5), queries, args.k)
        run = {
            "docs": n, "seed_seconds": round(seed_sec, 1), "vector_mapping_as_stored": mapping_now,
            "script_score": script_stats,
            "knn_nc2k": {**knn_stats, "num_candidates": args.k * 2, "overlap_with_script": overlap(script_ids, knn_ids, args.k)},
            "knn_nc5k": {**knn5_stats, "num_candidates": args.k * 5, "overlap_with_script": overlap(script_ids, knn5_ids, args.k)},
            "source_bytes": source_bytes(name, queries[0], args.k),
        }
        results["runs"].append(run)
        print(json.dumps(run, ensure_ascii=False, indent=1), flush=True)
        if not args.keep:
            es("DELETE", f"/{name}")

    print("raw:", save_raw("knn-bench", results, args.out))


if __name__ == "__main__":
    main()
