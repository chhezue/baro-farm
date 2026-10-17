# 로컬 도구 — 다른 모듈 없이 검색·추천·레시피를 끝까지 돌린다

`baro-shopping`이 없어 로컬에서 확인할 수 없던 것 두 가지를 채운다.

| 파일 | 무엇 |
|------|------|
| [`seed_catalog.py`](seed_catalog.py) | 카탈로그를 닮은 상품을 **운영과 같은 경로**(Kafka 상품 이벤트)로 심는다. 임베딩·재료명 추출을 앱이 실제로 수행한다 |
| [`cart_stub.py`](cart_stub.py) | `baro-shopping`의 장바구니 API 한 곳만 흉내 낸다. 레시피 추천 경로 전체를 통과시키기 위한 최소 스텁이다 |

레시피 추천은 장바구니 API를 Feign으로 부르기 때문에, 그 모듈 없이는 **이 경로 전체를 한 번도 확인하지 못했다**([`docs/audits/open-items.md`](../../docs/audits/open-items.md) A-2). 이 두 도구로 2026-09-07에 처음 통과시켰다.

## 1. 상품 심기

로컬 Elasticsearch·Kafka([`docker/local/`](../../docker/local/))가 떠 있고 앱이 기동해 있어야 한다.

```bash
python3 seed_catalog.py                 # 70건, 약 6분 (LLM 분당 한도를 피하려 일부러 천천히 보낸다)
python3 seed_catalog.py --dump-only     # 이미 색인돼 있으면 목록만 다시 저장
```

카테고리는 [`scripts/init-db/02-init-categories.sql`](../../../scripts/init-db/02-init-categories.sql)의 leaf 14개를 그대로 읽는다 — 목록을 스크립트에 옮겨 적으면 카탈로그가 바뀔 때 어긋나기 때문이다. 상품명만 스크립트가 갖는다. 색인이 끝나면 결과를 `seeded-products.json`으로 저장하고, 재료명이 빈 채로 색인된 상품이 있으면 알려 준다.

**상품명은 사람이 지은 것이다.** 카테고리 구조·재료명·벡터는 운영과 같은 방식으로 만들어지지만 상품명 자체는 실제 카탈로그가 아니므로, 검색 품질을 상품명 표본에 근거해 판단할 때는 이 점을 감안한다.

## 2. 레시피 경로 돌리기

스텁은 `seeded-products.json`에서 장바구니에 담을 상품을 고른다. 다른 곳에 두었으면 `STUB_PRODUCTS=/경로/파일.json`으로 넘긴다.

실제 LLM을 호출하므로 두 도구 모두 `BARO_AI_GEMINI_KEY`가 필요하고 무료 티어 한도를 쓴다.

```bash
# 1) 스텁을 띄운다 — 8081 포트, 장바구니에 담을 상품을 이름 조각으로 지정
python3 cart_stub.py 8081 "사과,계란,딸기"

# 2) 앱을 그 주소로 붙여 띄운다
ELASTICSEARCH_URIS=http://localhost:9201 \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9093 \
  SHOPPING_SERVICE_URL=http://localhost:8081 \
  SERVER_PORT=8093 \
  ./gradlew :baro-ai:bootRun

# 3) 부른다
curl "http://localhost:8093/api/v1/recommendations/recipes/{userId}"
```

## 알아 둘 것

- 스텁은 **경로를 가리지 않고** 어떤 GET에도 같은 장바구니를 돌려준다. Feign이 부르는 경로가 바뀌어도 통과하므로 경로 계약 자체는 검증하지 못한다
- 담긴 상품을 바꾸려면 스텁을 다시 띄운다 — 인자를 시작할 때 한 번만 읽는다
- 이 스텁으로 확인한 결과는 [`audits/cart-recipe/007`](../../docs/audits/cart-recipe/007-fruit-exclusion-and-prompt-example.md) M-4에 있다
