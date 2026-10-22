"""M-3 (ES 쪽) — 상품 문서 N건을 1건씩 저장 vs `_bulk`로 저장했을 때의 소요 시간.

색인 경로가 상품마다 `save`를 2번(메인·자동완성) 부르는 현재 구조와, `saveAll`(bulk)로 묶는 구조의 차이를
ES 왕복 비용만 떼어 잰다. 문서는 실제 `product` 문서처럼 1536차원 벡터를 포함한다(문서당 약 21KB).

사용: python3 bulk_vs_single.py [--docs 1000] [--bulk-size 100]
"""

import argparse
import json
import os
import time

from common import DIMS, es, ms, random_unit_vector, save_raw, seeded

INDEX = "bench_bulk"
MAPPING = {
    "settings": {"number_of_shards": 1, "number_of_replicas": 0, "refresh_interval": "1s"},
    "mappings": {"properties": {
        "productName": {"type": "text"},
        "status": {"type": "keyword"},
        "vector": {"type": "dense_vector", "dims": DIMS},
    }},
}


def doc(i, rng):
    return {"productName": f"벤치 상품 {i}", "status": "ON_SALE", "vector": random_unit_vector(rng)}


def recreate():
    es("DELETE", f"/{INDEX}")
    status, body, _, _ = es("PUT", f"/{INDEX}", MAPPING)
    assert status == 200, body


def run_single(docs):
    t0 = time.perf_counter()
    for i, d in enumerate(docs):
        status, body, _, _ = es("PUT", f"/{INDEX}/_doc/{i}", d)
        assert status in (200, 201), body
    return time.perf_counter() - t0


def run_bulk(docs, bulk_size):
    t0 = time.perf_counter()
    for start in range(0, len(docs), bulk_size):
        lines = []
        for i, d in enumerate(docs[start:start + bulk_size], start):
            lines.append(json.dumps({"index": {"_index": INDEX, "_id": str(i)}}))
            lines.append(json.dumps(d))
        status, body, _, _ = es("POST", "/_bulk", "\n".join(lines) + "\n")
        assert status == 200 and not body.get("errors"), body
    return time.perf_counter() - t0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--docs", type=int, default=1000)
    ap.add_argument("--bulk-size", type=int, default=100)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "..", "docs", "audits", "keyword-search", "vectorbench"))
    args = ap.parse_args()

    rng = seeded()
    docs = [doc(i, rng) for i in range(args.docs)]
    doc_bytes = len(json.dumps(docs[0]).encode())

    recreate()
    single = run_single(docs)
    recreate()
    bulk = run_bulk(docs, args.bulk_size)

    result = {
        "docs": args.docs, "bulk_size": args.bulk_size, "doc_bytes": doc_bytes,
        "single_seconds": round(single, 3), "single_docs_per_sec": round(args.docs / single, 1),
        "bulk_seconds": round(bulk, 3), "bulk_docs_per_sec": round(args.docs / bulk, 1),
        "speedup": round(single / bulk, 2),
        "es_version": es("GET", "/")[1]["version"]["number"],
    }
    print(json.dumps(result, ensure_ascii=False, indent=1))
    print("raw:", save_raw("bulk-vs-single", result, args.out))
    es("DELETE", f"/{INDEX}")


if __name__ == "__main__":
    main()
