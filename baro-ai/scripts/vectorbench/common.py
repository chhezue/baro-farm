"""벤치마크 스크립트 공통 — 표준 라이브러리만 쓴다(requests·numpy 없음).

로컬 Elasticsearch 주소는 환경변수 BENCH_ES_URL(기본 http://localhost:9200),
Gemini 키는 BARO_AI_GEMINI_KEY. 키 값은 절대 출력하지 않는다.
"""

import json
import math
import os
import random
import statistics
import time
import urllib.error
import urllib.request

ES_URL = os.environ.get("BENCH_ES_URL", "http://localhost:9200")
GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001"
DIMS = 1536


def http(method, url, body=None, headers=None, timeout=120):
    """JSON 요청 1회. (status, parsed_body, elapsed_seconds, response_bytes)를 돌려준다."""
    data = None
    hdrs = {"Content-Type": "application/json"}
    if headers:
        hdrs.update(headers)
    if body is not None:
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None), time.perf_counter() - t0, len(raw)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = raw.decode(errors="replace")
        return e.code, parsed, time.perf_counter() - t0, len(raw)


def es(method, path, body=None, timeout=300):
    return http(method, ES_URL + path, body, timeout=timeout)


def gemini_key():
    key = os.environ.get("BARO_AI_GEMINI_KEY")
    if not key:
        raise SystemExit("BARO_AI_GEMINI_KEY가 없습니다. 예: eval \"$(grep -m1 '^export BARO_AI_GEMINI_KEY' ~/.zshrc)\"")
    return key


def random_unit_vector(rng, dims=DIMS):
    v = [rng.gauss(0, 1) for _ in range(dims)]
    n = math.sqrt(sum(x * x for x in v))
    return [x / n for x in v]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    if na == 0 or nb == 0:
        return float("nan")
    return dot / (na * nb)


def percentiles(values):
    s = sorted(values)
    def p(q):
        if not s:
            return float("nan")
        k = (len(s) - 1) * q
        f = math.floor(k)
        c = math.ceil(k)
        if f == c:
            return s[int(k)]
        return s[f] + (s[c] - s[f]) * (k - f)
    return {"n": len(s), "p50": p(0.5), "p95": p(0.95), "p99": p(0.99), "mean": statistics.fmean(s) if s else float("nan"), "max": s[-1] if s else float("nan")}


def ms(x):
    return round(x * 1000, 2)


def save_raw(name, payload, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    path = os.path.join(out_dir, f"{name}-{stamp}.json")
    with open(path, "w") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
    return path


def seeded(seed=42):
    return random.Random(seed)
