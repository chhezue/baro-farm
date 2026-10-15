#!/usr/bin/env python3
"""회원 검색을 몰아넣고 비동기 부가 작업(검색 로그 적재 + 프로필 갱신)이 얼마나 버려지는지 잰다.

`profileUpdateExecutor`는 상주 2 / 대기열 100 / 최대 5다. 자바 ThreadPoolExecutor는 대기열이
가득 차야 최대치까지 스레드를 늘리므로 상시 2개로 돌고, 대기열 100칸이 차면 그 뒤 제출은
거부 정책(로그만 남기고 폐기)에 걸린다. 이 스크립트는 "몇 %가 폐기되는가"를 센다.

세는 방법은 두 가지이고 서로 검산이 된다.
  (1) 앱 로그의 "대기열이 가득 차" 줄 수 증가분  → 폐기된 수
  (2) search_logs 인덱스의 문서 수 증가분        → 적재된 수
(1) + (2)가 보낸 요청 수와 같아야 한다.

사용법 (앱·ES가 이미 떠 있어야 한다 — docker/local/docker-compose.yml 참고):
  python3 async_queue.py --app-log /path/to/app.log --out result.json
"""
import argparse, json, statistics, time, urllib.parse, urllib.request
import concurrent.futures as cf

KEYWORDS = ["사과", "계란", "대파", "삼겹살", "김치", "감자", "양파", "고등어"]
REJECT_MARK = "대기열이 가득 차"


def count_lines(path, needle):
    with open(path, encoding="utf-8", errors="replace") as f:
        return sum(1 for line in f if needle in line)


def es_count(es, index):
    with urllib.request.urlopen(f"{es}/{index}/_count") as r:
        return json.load(r)["count"]


def search_once(api, user_id, i):
    url = f"{api}/search?q={urllib.parse.quote(KEYWORDS[i % len(KEYWORDS)])}&size=10"
    req = urllib.request.Request(url, headers={"X-User-Id": user_id})
    t = time.perf_counter()
    urllib.request.urlopen(req).read()
    return (time.perf_counter() - t) * 1000


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--api", default="http://localhost:8092/api/v1")
    p.add_argument("--es", default="http://localhost:9201")
    p.add_argument("--app-log", required=True, help="bootRun 로그 파일 — 폐기 줄을 센다")
    p.add_argument("--user-id", default="11111111-1111-1111-1111-111111111111")
    p.add_argument("--concurrency", type=int, nargs="+", default=[1, 10, 30])
    p.add_argument("--requests", type=int, default=300)
    p.add_argument("--out", default="async-queue.json")
    a = p.parse_args()

    rounds = []
    for conc in a.concurrency:
        # ES refresh(1초)와 실행자 소진을 기다린 뒤 기준선을 잡는다
        time.sleep(3)
        base_rej, base_log = count_lines(a.app_log, REJECT_MARK), es_count(a.es, "search_logs")

        with cf.ThreadPoolExecutor(conc) as ex:
            lat = sorted(ex.map(lambda i: search_once(a.api, a.user_id, i), range(a.requests)))

        time.sleep(15)  # 큐에 남은 작업이 다 돌고 ES가 refresh될 때까지
        rej = count_lines(a.app_log, REJECT_MARK) - base_rej
        logged = es_count(a.es, "search_logs") - base_log

        rounds.append({
            "concurrency": conc,
            "requests": a.requests,
            "logged": logged,
            "rejected": rej,
            "accounted": logged + rej,          # requests와 같아야 한다
            "loss_rate": round(rej / a.requests, 4),
            "latency_ms": {
                "p50": round(statistics.median(lat), 1),
                "p95": round(lat[int(len(lat) * 0.95)], 1),
                "max": round(lat[-1], 1),
            },
        })
        r = rounds[-1]
        print(f"동시 {conc:>2}  적재 {logged:>3}  폐기 {rej:>3}  "
              f"(합 {r['accounted']}/{a.requests})  유실률 {r['loss_rate']:.0%}  "
              f"p95 {r['latency_ms']['p95']}ms")

    out = {"measuredAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
           "executor": {"core": 2, "max": 5, "queueCapacity": 100},
           "rounds": rounds}
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("저장:", a.out)


if __name__ == "__main__":
    main()
