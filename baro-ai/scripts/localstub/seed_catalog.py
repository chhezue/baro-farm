"""로컬 Elasticsearch에 카탈로그를 닮은 상품을 심는다 — 운영과 같은 경로(Kafka 상품 이벤트)로.

**왜 ES에 직접 넣지 않나**: 상품이 색인되는 실제 경로에는 임베딩 생성과 LLM 재료명 추출이 들어 있다.
직접 넣으면 그 둘을 건너뛰게 되고, 재료명이 사람이 손으로 적은 값이 된다. 그러면 레시피 추천을 확인해도
운영과 같은 조건이 아니다. 이 스크립트는 이벤트만 던지고 색인은 앱이 하게 둔다.

**카테고리는 옮겨 적지 않는다** — `scripts/init-db/02-init-categories.sql`에서 읽는다. 카탈로그가 바뀌면
여기도 따라 바뀌어야 하는데, 목록을 복사해 두면 한쪽만 고쳐져 어긋나기 때문이다. 이 파일이 갖는 것은
상품명뿐이다.

**LLM 호출 속도를 일부러 늦춘다.** 상품 1건마다 재료명 추출이 1회 나가는데 무료 티어는 분당 호출 수에
제한이 있다. 묶음(기본 5건 — 앱의 poll 크기와 같다)마다 쉬어 분당 호출을 제한 아래로 유지한다.
"""
import argparse, json, os, re, subprocess, sys, time, urllib.request, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
SQL = os.path.abspath(os.path.join(HERE, "..", "..", "..", "scripts", "init-db", "02-init-categories.sql"))

# 카테고리 코드 -> 상품명. 실제로 이 쇼핑몰이 팔 법한 이름으로 짓되 브랜드·산지·수량 표기를 섞어,
# 색인 시점 재료명 추출이 실제로 할 일(수식어 걷어내기)을 하게 만든다.
CATALOG = {
    "AGRI_FRUIT_SEASONAL": ["제주 서귀포 노지 감귤 3kg", "성주 꿀참외 2kg", "충주 사과 홍로 3kg",
                            "나주 신고배 5과", "고당도 수박 8kg 1통"],
    "AGRI_FRUIT_STORAGE":  ["경북 부사 사과 10kg 박스", "상주 곶감 반건시 1kg", "제주 한라봉 3kg",
                            "영동 샤인머스캣 2kg", "청도 반시 홍시 1.5kg"],
    "AGRI_FRUIT_BERRY":    ["논산 설향 딸기 500g", "고창 복분자 1kg", "제주 블루베리 500g",
                            "국산 아로니아 1kg", "문경 오미자 1kg"],
    "AGRI_VEG_SALAD":      ["국내산 청상추 200g", "친환경 어린잎채소 150g", "국산 양상추 1통",
                            "국산 루꼴라 100g", "국내산 깻잎 100g"],
    "AGRI_VEG_SOUP":       ["국산 배추 1포기", "친환경 대파 1단", "국산 애호박 2개",
                            "해남 무 1개", "국산 시금치 300g"],
    "AGRI_VEG_STIRFRY":    ["국산 양파 1.5kg", "청양고추 200g", "국산 애느타리버섯 300g",
                            "국산 브로콜리 2송이", "국산 오이 5개"],
    "AGRI_VEG_ROOT":       ["강원 감자 3kg", "해남 고구마 3kg", "국산 당근 1kg",
                            "의성 햇마늘 1kg", "국산 연근 500g"],
    "AGRI_GRAIN_STAPLE":   ["철원 오대쌀 10kg", "국산 찰보리쌀 2kg", "국산 현미 4kg",
                            "국산 검정콩 서리태 1kg", "국산 찹쌀 2kg"],
    "AGRI_SPECIAL_MEDICINAL": ["횡성 더덕 500g", "국산 도라지 500g", "청도 구기자 300g",
                               "국산 표고버섯 400g", "금산 수삼 4년근 300g"],
    "AGRI_NUT_RAW":        ["국산 햇호두 500g", "국산 밤 1kg", "국산 볶음땅콩 500g",
                            "가평 잣 200g", "국산 아몬드 500g"],
    "AGRI_LOCAL_REPRESENT": ["횡성 한우 등심 300g", "완도 다시마 200g", "영양 건고추 500g",
                             "순창 재래식 된장 1kg", "여수 돌게장 500g"],
    "AGRI_LIVESTOCK_EGG":  ["동물복지 유정란 10구", "무항생제 대란 30구", "국산 메추리알 200g",
                            "친환경 특란 15구", "국산 오리알 10구"],
    "AGRI_LIVESTOCK_MEAT": ["국내산 삼겹살 500g", "무항생제 닭볶음탕용 1kg", "국내산 돼지 목살 600g",
                            "횡성 한우 국거리 400g", "국내산 닭가슴살 1kg"],
    "AGRI_LIVESTOCK_DAIRY": ["국산 1A 우유 900ml", "국산 플레인 요거트 450g", "국산 생크림 500ml",
                             "국산 자연치즈 200g", "국산 버터 200g"],
}

NAMESPACE = uuid.UUID("00000000-0000-0000-0000-0000000000c0")


def load_categories():
    """카테고리 SQL에서 leaf(level 2) 카테고리의 UUID·이름을 읽는다."""
    s = open(SQL, encoding="utf-8").read()
    uuids = dict(re.findall(r"SET @(\w+)\s*=\s*'([0-9a-f-]{36})';", s))
    rows = re.findall(
        r"UUID_TO_BIN\(@(\w+)\),\s*\n?\s*'([^']+)',\s*\n?\s*'([A-Z_]+)',\s*\n?\s*"
        r"(?:UUID_TO_BIN\(@\w+\)|NULL),\s*\n?\s*(\d+)", s)
    return {code: (uuids[var], name) for var, name, code, lvl in rows if lvl == "2"}


def build_events(categories, price_base=3900):
    events = []
    for code, names in CATALOG.items():
        if code not in categories:
            sys.exit(f"카탈로그 SQL에 없는 카테고리입니다: {code}")
        cat_id, cat_name = categories[code]
        for i, name in enumerate(names):
            events.append({"type": "PRODUCT_CREATED", "data": {
                "productId": str(uuid.uuid5(NAMESPACE, name)),
                "productName": name,
                "productCategoryId": cat_id,
                "productCategoryCode": code,
                "productCategoryName": cat_name,
                "price": price_base + (i * 1700) + (len(name) * 130),
                "status": "ON_SALE",
                "updatedAt": "2026-09-07T00:00:00Z"}})
    return events


def publish(events, container, topic, wave, pause):
    """Kafka로 이벤트를 보낸다. 묶음마다 쉬어 앱의 LLM 호출이 분당 한도를 넘지 않게 한다."""
    for start in range(0, len(events), wave):
        chunk = events[start:start + wave]
        payload = "\n".join(json.dumps(e, ensure_ascii=False) for e in chunk) + "\n"
        subprocess.run(["docker", "exec", "-i", container, "kafka-console-producer",
                        "--bootstrap-server", "kafka:29092", "--topic", topic],
                       input=payload.encode(), check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(f"  발행 {min(start + wave, len(events))}/{len(events)}", flush=True)
        if start + wave < len(events):
            time.sleep(pause)


def dump_indexed(es_url, out_path, expected):
    """색인된 결과를 그대로 읽어 스텁이 쓸 목록으로 저장한다 — 재료명은 앱이 뽑은 값이어야 한다."""
    urllib.request.urlopen(urllib.request.Request(f"{es_url}/product/_refresh", method="POST")).read()
    body = json.dumps({"size": 500, "_source": {"excludes": ["vector"]},
                       "query": {"match_all": {}}}).encode()
    req = urllib.request.Request(f"{es_url}/product/_search", data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    hits = json.load(urllib.request.urlopen(req))["hits"]["hits"]
    rows = [{"productId": h["_source"]["productId"], "productName": h["_source"]["productName"],
             "categoryCode": h["_source"]["productCategoryCode"],
             "categoryName": h["_source"]["productCategoryName"],
             "ingredientName": h["_source"].get("ingredientName", "")} for h in hits]
    rows.sort(key=lambda r: (r["categoryCode"], r["productName"]))
    json.dump(rows, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    empty = [r["productName"] for r in rows if not r["ingredientName"]]
    print(f"\n색인 {len(rows)}/{expected}건 · 저장: {out_path}")
    if empty:
        print(f"재료명이 비어 색인된 상품 {len(empty)}건 (LLM 호출 실패): {empty[:5]}")
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--es", default="http://localhost:9201")
    ap.add_argument("--container", default="baro-ai-local-kafka")
    ap.add_argument("--topic", default="product-events")
    ap.add_argument("--wave", type=int, default=5, help="한 번에 보낼 이벤트 수 (앱의 poll 크기와 같게)")
    ap.add_argument("--pause", type=float, default=25.0, help="묶음 사이 대기 초 — LLM 분당 한도 회피용")
    ap.add_argument("--out", default=os.path.join(HERE, "seeded-products.json"))
    ap.add_argument("--dump-only", action="store_true", help="발행 없이 색인 결과만 저장")
    a = ap.parse_args()

    events = build_events(load_categories())
    if not a.dump_only:
        print(f"상품 {len(events)}건을 {a.topic}으로 발행합니다 "
              f"(묶음 {a.wave}건, 사이 {a.pause}초 — 예상 {len(events) / a.wave * a.pause / 60:.0f}분)")
        publish(events, a.container, a.topic, a.wave, a.pause)
        print("\n발행 완료. 앱이 마지막 묶음을 색인할 때까지 기다립니다...")
        time.sleep(a.pause)
    dump_indexed(a.es, a.out, len(events))


if __name__ == "__main__":
    sys.exit(main())
