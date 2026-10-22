"""M-3 (임베딩 쪽) — 상품명 N개를 1개씩 N회 vs 배치 B개씩 호출했을 때의 총 소요 시간.

`ProductEmbeddingService`는 목록을 받는 `embed(List)`를 항목 1개짜리 목록으로 부른다. Spring AI의 Gemini 구현은
목록 전체를 한 번의 `embedContent`(REST `batchEmbedContents`)로 보내므로, 여기서는 그 REST를 직접 불러 배치 효과를 잰다.
같은 `gemini-embedding-001`, `outputDimensionality=1536`(운영과 동일).

무료 티어 분당 한도에 걸리면 429가 난다 — 그 경우 그대로 기록하고 중단한다(재시도로 숫자를 흐리지 않는다).

사용: eval "$(grep -m1 '^export BARO_AI_GEMINI_KEY' ~/.zshrc)" && python3 embed_batch.py [--n 50] [--batch 10 50]
"""

import argparse
import os
import time

from common import GEMINI_URL, gemini_key, http, ms, percentiles, save_raw

NAMES = [
    "제주 서귀포 고당도 노지 감귤 3kg", "청송 꿀사과 부사 5kg 가정용", "곰곰 무항생제 신선한 대란 30구", "국내산 애호박 1개",
    "강원 고랭지 양배추 1통", "해남 세척 당근 1kg", "의성 마늘 깐마늘 500g", "횡성 한우 등심 300g 1등급",
    "제주 흑돼지 삼겹살 500g", "완도 활 전복 1kg 10미", "국내산 생물 고등어 2마리", "논산 딸기 설향 500g",
    "나주 신고배 3kg", "성주 참외 2kg", "상주 곶감 반건시 1kg", "국내산 깐 밤 500g", "무안 양파 3kg 햇양파",
    "진도 대파 1단", "홍성 시금치 300g", "국내산 표고버섯 300g", "새송이버섯 400g", "국내산 팽이버섯 150g 3봉",
    "국내산 감자 3kg 수미", "해남 꿀고구마 3kg 베니하루카", "청양 고춧가루 1kg", "국내산 방울토마토 1kg 대추방울",
    "국산 오이 3개", "국내산 가지 3개", "국내산 브로콜리 1송이", "국내산 파프리카 빨강 2개", "국내산 상추 200g 청상추",
    "국내산 깻잎 100g", "경북 영양 건고추 500g", "국산 콩나물 300g", "국산 두부 300g 부침용", "종가집 포기김치 3kg",
    "순창 재래식 된장 1kg", "순창 고추장 1kg 태양초", "국산 청국장 500g", "국산 콩비지 300g", "국산 총각김치 1kg",
    "안동 생강 500g", "국산 쪽파 300g", "국산 미나리 200g", "국산 부추 300g", "국내산 무 1개", "국산 배추 1포기",
    "국산 단호박 1개", "국산 옥수수 5개 찰옥수수", "제주 초당옥수수 5개",
]


def embed_single(key, text):
    body = {"content": {"parts": [{"text": text}]}, "outputDimensionality": 1536}
    return http("POST", f"{GEMINI_URL}:embedContent", body, headers={"x-goog-api-key": key})


def embed_batch(key, texts):
    body = {"requests": [
        {"model": "models/gemini-embedding-001", "content": {"parts": [{"text": t}]}, "outputDimensionality": 1536}
        for t in texts
    ]}
    return http("POST", f"{GEMINI_URL}:batchEmbedContents", body, headers={"x-goog-api-key": key})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=50)
    ap.add_argument("--batch", type=int, nargs="+", default=[10, 50])
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "..", "docs", "audits", "keyword-search", "vectorbench"))
    args = ap.parse_args()
    key = gemini_key()
    texts = (NAMES * ((args.n // len(NAMES)) + 1))[:args.n]
    result = {"n": args.n, "model": "gemini-embedding-001", "dims": 1536, "arms": {}}

    # 1건씩
    latencies = []
    t0 = time.perf_counter()
    for t in texts:
        status, body, elapsed, _ = embed_single(key, t)
        if status != 200:
            result["arms"]["single"] = {"error": status, "body": str(body)[:300], "completed": len(latencies)}
            break
        latencies.append(elapsed)
    else:
        total = time.perf_counter() - t0
        result["arms"]["single"] = {"requests": len(texts), "total_seconds": round(total, 3), "per_call_ms": percentiles([ms(x) for x in latencies])}
    print("single:", result["arms"]["single"])

    # 배치
    for b in args.batch:
        latencies = []
        ok = True
        t0 = time.perf_counter()
        for start in range(0, len(texts), b):
            chunk = texts[start:start + b]
            status, body, elapsed, _ = embed_batch(key, chunk)
            if status != 200:
                result["arms"][f"batch{b}"] = {"error": status, "body": str(body)[:300], "completed_requests": len(latencies)}
                ok = False
                break
            got = len(body.get("embeddings", []))
            assert got == len(chunk), (got, len(chunk))
            latencies.append(elapsed)
        if ok:
            total = time.perf_counter() - t0
            result["arms"][f"batch{b}"] = {"requests": len(latencies), "total_seconds": round(total, 3), "per_call_ms": percentiles([ms(x) for x in latencies])}
        print(f"batch{b}:", result["arms"][f"batch{b}"])
        time.sleep(2)

    print("raw:", save_raw("embed-batch", result, args.out))


if __name__ == "__main__":
    main()
