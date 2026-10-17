# 취향 벡터가 만들어지는 과정 (예시)

사용자 한 명의 행동 로그가 `UserProfileEmbeddingDocument` 하나로 바뀌는 과정을 실제 숫자로 따라간다. 계산 규칙 자체의 SSOT는 [`specs/taste-vector/taste-vector-pipeline.md`](specs/taste-vector/taste-vector-pipeline.md) 비즈니스 규칙 표이고, 이 문서는 그 규칙을 한 사례에 적용해 "왜 이런 벡터가 나오는지"를 보여준다.

## 언제 이 문서를 보는가

- 특정 사용자에게 왜 그 상품이 추천됐는지를 로그·가중치 수준에서 되짚어야 할 때
- 가중치 규칙을 바꾸기 전에 그 변경이 결과를 얼마나 움직이는지 감을 잡을 때
- 취향 벡터 계산에 처음 손대는 사람이 전체 흐름을 한 사례로 훑을 때

행동 하나가 어떤 비중을 갖는지, 삭제한 상품이 왜 결과에 전혀 남지 않는지, 그 비중이 어떻게 벡터 하나로 합쳐지는지를 알 수 있다.

> **2026-09-07 기준이다.** 이 예시에 검색 로그가 없는 이유는 검색을 취향 신호에서 뺐기 때문이고, 비중에 수량을 곱하지 않는 이유도 그날 그 축을 뺐기 때문이다 — 근거는 [`audits/taste-vector/005`](audits/taste-vector/005-signal-and-weight-simplification.md)에 있다.
>
> **2026-09-05 재설계 반영본이다.** 예전에는 상품명을 가중치만큼 반복한 긴 문장을 만들어 Gemini 임베딩 API에 보냈다. 지금은 그 호출이 없다 — 로그에 등장한 상품이 색인 시점에 이미 갖고 있는 벡터를 비중대로 섞는다. 바꾼 이유는 [`audits/taste-vector/001-profile-refresh-path.md`](audits/taste-vector/001-profile-refresh-path.md) "A-3 재설계" 절에 있다.

## 입력 데이터

사용자 ID: `550e8400-e29b-41d4-a716-446655440000`

등장하는 상품 4개는 모두 `product` 인덱스에 색인돼 있고 각자 768차원 벡터를 갖고 있다.

| 표기 | productId 끝자리 | 상품명 |
|------|------------------|--------|
| P1 | `…0001` | 청송사과 프리미엄 1kg |
| P2 | `…0002` | 바나나 1송이 |
| P3 | `…0003` | 오렌지 1kg |
| P4 | `…0004` | 홍로사과 2kg |

### 1. 장바구니 로그 (최신 5건까지)

```json
[
  { "productId": "…0001", "eventType": "CART_ITEM_ADDED",       "occurredAt": "1시간 전" },
  { "productId": "…0002", "eventType": "CART_ITEM_ADDED",       "occurredAt": "1일 전" },
  { "productId": "…0004", "eventType": "CART_QUANTITY_UPDATED", "occurredAt": "1일 전" },
  { "productId": "…0003", "eventType": "CART_ITEM_REMOVED",     "occurredAt": "2일 전" }
]
```

### 2. 주문 로그 (최신 5건까지)

```json
[
  { "productId": "…0001", "eventType": "ORDER_CONFIRMED", "occurredAt": "30분 전" }
]
```

총 5건이라 최소 로그 개수(3건)를 넘겼으므로 계산을 진행한다. 로그에 남는 것은 위 네 항목뿐이다 — 수량과 카테고리 코드는 상류가 보내지만 2026-09-07부터 받지도 저장하지도 않는다.

## 비중 계산

로그 1건의 비중은 **행동 종류 하나로** 정해진다. 주문 확정 3 · 장바구니 담기 2 · 수량 변경 1이고, 삭제·취소는 0이다. 곱하는 축이 없어서 범위가 0 또는 1~3이다.

> 한때 여기에 두 축을 더 곱했다. **시간 감쇠**(최근 3.0에서 7일마다 절반)는 유형별 최근 5건만 읽는 구조에서 한 번의 쇼핑 로그가 전부 같은 시간대라 감쇠값이 같고, 마지막 L2 정규화가 그 배수를 소거해 결과에 영향이 없어서 2026-09-06에 뺐다([`002`](audits/taste-vector/002-weight-axes-and-tests.md) F-1·F-2). **수량**은 선호의 세기가 아니라 소비량이어서 2026-09-07에 뺐다([`005`](audits/taste-vector/005-signal-and-weight-simplification.md) A-2). 발생 시각은 30일 조회 창과 최신순 정렬에 쓰이므로 계속 저장하지만, 수량은 아예 저장하지 않게 됐다([`cross-cutting/003`](audits/cross-cutting/003-stale-comments-and-write-only-fields.md) B-1).

### 장바구니

| 상품 | 이벤트 | 비중 |
|------|--------|------|
| P1 | CART_ITEM_ADDED | 2 |
| P2 | CART_ITEM_ADDED | 2 |
| P4 | CART_QUANTITY_UPDATED | 1 |
| P3 | CART_ITEM_REMOVED | **0 — 완전히 제외** |

P1은 3개, P2는 2개를 담았지만 비중은 똑같이 2다. "몇 개 담았나"가 아니라 "담았나"만 본다.

P3은 비중이 0이라 평균에서 빠질 뿐 아니라 경험 상품 목록에도 들어가지 않는다. 관심이 없어진 상품이므로 나중에 추천에 다시 나와도 된다.

### 주문

| 상품 | 이벤트 | 비중 |
|------|--------|------|
| P1 | ORDER_CONFIRMED | 3 |

## 상품별 합계

| 상품 | 장바구니 | 주문 | 합계 | 전체 대비 |
|------|----------|------|------|-----------|
| P1 청송사과 프리미엄 1kg | 2 | 3 | **5** | 62.5% |
| P2 바나나 1송이 | 2 | — | **2** | 25.0% |
| P4 홍로사과 2kg | 1 | — | **1** | 12.5% |
| P3 오렌지 1kg | 0 | — | — | 0% |

## 벡터 합치기

상품 벡터를 비중대로 더한 뒤 길이를 1로 맞춘다(L2 정규화).

```
프로필 = 정규화( 5 × v(P1) + 2 × v(P2) + 1 × v(P4) )
```

비중의 합(8)으로 나누는 단계는 코드에 없다. 마지막에 길이를 1로 맞추기 때문에 그 전에 전체를 같은 수로 나누든 말든 방향이 같아서다. 길이를 맞추는 것은 생략할 수 없다 — 상품 벡터와 코사인 유사도로 비교하려면 같은 기준이어야 한다.

벡터가 아직 없는 상품(색인 실패 등)은 조용히 건너뛴다. 쓸 수 있는 벡터가 하나도 없으면 프로필을 저장하지 않고, 그 사용자의 개인화 추천은 빈 목록이 된다.

## 저장 결과

```json
{
  "_id": "550e8400-e29b-41d4-a716-446655440000",
  "_index": "user_profile_embeddings",
  "_source": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "userProfileVector": [0.0123456, -0.0234567, "… 총 768개"],
    "lastUpdatedAt": "2026-01-12T12:30:00Z",
    "sourceProductIds": ["…0001", "…0002", "…0004"]
  }
}
```

`sourceProductIds`는 **비중이 0이 아니었던** 상품이다. 개인화 추천은 이 상품들을 후보에서 뺀다 — 이미 담거나 산 것을 다시 권하지 않기 위해서다. 삭제한 P3은 비중이 0이라 여기 없고, 그래서 나중에 추천 후보로 다시 나올 수 있다.

### 인덱스 매핑

```json
{
  "mappings": {
    "properties": {
      "userId": { "type": "keyword" },
      "userProfileVector": { "type": "dense_vector", "dims": 768, "index": true, "similarity": "cosine" },
      "lastUpdatedAt": { "type": "date" },
      "sourceProductIds": { "type": "keyword" }
    }
  }
}
```

## 어떻게 쓰이는가

```java
UserProfileEmbeddingDocument profile = userProfileEmbeddingRepository.findById(userId).orElse(null);
float[] userVector = profile.getUserProfileVector();

// 경험 상품을 뺀 뒤 코사인 유사도로 후보를 뽑고, 다양화로 topK개를 고른다.
List<ProductRecommendResponse> recommendations =
    vectorRecommendPipeline.recommend(userVector, profile.getSourceProductIds(), topK);
```

이 예시의 프로필은 P1 쪽으로 강하게 기울어 있으므로, 추천에는 경험 상품(P1·P2·P4)을 뺀 나머지 중 사과·과일과 가까운 상품이 먼저 올라온다. 카테고리 보너스는 주지 않는다 — 벡터 자체가 이미 그 방향으로 기울어 있어 같은 정보를 두 번 주는 셈이기 때문이다.

> 저장 타입은 `float[]`이다(2026-09-05 상품 벡터와 통일). ES에 들어가는 JSON은 숫자 배열 그대로라 이미 만들어진 프로필 문서는 그대로 읽힌다.

## 관련 문서

- [`specs/taste-vector/taste-vector-pipeline.md`](specs/taste-vector/taste-vector-pipeline.md) — 계산 규칙·값의 SSOT
- [`specs/vector-similarity/vector-similarity-search.md`](specs/vector-similarity/vector-similarity-search.md) — 이 벡터로 후보를 뽑는 벡터 검색·다양화
- [`audits/taste-vector/001-profile-refresh-path.md`](audits/taste-vector/001-profile-refresh-path.md) — 재설계 배경과 근거
- [`product/glossary.md`](product/glossary.md) — 용어
