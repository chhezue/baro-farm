# 취향 벡터가 만들어지는 과정 (예시)

사용자 한 명의 행동 로그가 `UserProfileEmbeddingDocument` 하나로 바뀌는 과정을 실제 숫자로 따라간다. 계산 규칙 자체의 SSOT는 [`specs/taste-vector/taste-vector-pipeline.md`](specs/taste-vector/taste-vector-pipeline.md) 비즈니스 규칙 표이고, 이 문서는 그 규칙을 한 사례에 적용해 "왜 이런 벡터가 나오는지"를 보여준다.

## 언제 이 문서를 보는가

- 특정 사용자에게 왜 그 상품이 추천됐는지를 로그·가중치 수준에서 되짚어야 할 때
- 가중치 규칙을 바꾸기 전에 그 변경이 결과를 얼마나 움직이는지 감을 잡을 때
- 취향 벡터 계산에 처음 손대는 사람이 전체 흐름을 한 사례로 훑을 때

행동 하나가 어떤 비중을 갖는지, 삭제한 상품이 왜 결과에 전혀 남지 않는지, 검색이 어떻게 상품 벡터로 바뀌는지를 알 수 있다.

> **2026-09-05 재설계 반영본이다.** 예전에는 상품명을 가중치만큼 반복한 긴 문장을 만들어 Gemini 임베딩 API에 보냈다. 지금은 그 호출이 없다 — 로그에 등장한 상품이 색인 시점에 이미 갖고 있는 벡터를 비중대로 섞는다. 바꾼 이유는 [`audits/taste-vector/001-profile-refresh-path.md`](audits/taste-vector/001-profile-refresh-path.md) "A-3 재설계" 절에 있다.

## 입력 데이터

사용자 ID: `550e8400-e29b-41d4-a716-446655440000`

등장하는 상품 4개는 모두 `product` 인덱스에 색인돼 있고 각자 1536차원 벡터를 갖고 있다.

| 표기 | productId 끝자리 | 상품명 |
|------|------------------|--------|
| P1 | `…0001` | 청송사과 프리미엄 1kg |
| P2 | `…0002` | 바나나 1송이 |
| P3 | `…0003` | 오렌지 1kg |
| P4 | `…0004` | 홍로사과 2kg |

### 1. 검색 로그 (최신 5건까지)

검색어 문자열이 아니라 **그때 상위에 나온 상품 ID**(`topProductIds`, 최대 3개)를 함께 저장한다.

```json
[
  { "searchQuery": "사과",   "topProductIds": ["…0001", "…0004"], "searchedAt": "1시간 전" },
  { "searchQuery": "바나나", "topProductIds": ["…0002"],          "searchedAt": "1일 전" },
  { "searchQuery": "청송사과", "topProductIds": ["…0001"],        "searchedAt": "2일 전" }
]
```

### 2. 장바구니 로그 (최신 5건까지)

```json
[
  { "productId": "…0001", "eventType": "CART_ITEM_ADDED",   "quantity": 3, "occurredAt": "1시간 전" },
  { "productId": "…0002", "eventType": "CART_ITEM_ADDED",   "quantity": 2, "occurredAt": "1일 전" },
  { "productId": "…0003", "eventType": "CART_ITEM_REMOVED", "quantity": 1, "occurredAt": "2일 전" }
]
```

### 3. 주문 로그 (최신 5건까지)

```json
[
  { "productId": "…0001", "eventType": "ORDER_CONFIRMED", "quantity": 2, "occurredAt": "30분 전" },
  { "productId": "…0002", "eventType": "ORDER_CONFIRMED", "quantity": 1, "occurredAt": "1일 전" }
]
```

총 8건이라 최소 로그 개수(3건)를 넘겼으므로 계산을 진행한다.

## 비중 계산

각 로그의 비중은 `행동 × 수량 × 시간`이다. 반올림하지 않고 소수 그대로 쓴다.

**시간 가중치**는 방금 한 행동이 3.0에서 시작해 7일마다 절반이 되고, 아무리 오래돼도 0.3 아래로는 내려가지 않는다. 경과 시간은 시간 단위로 세므로 30분 전은 0시간으로 본다.

| 경과 | 시간 가중치 |
|------|-----------|
| 30분 전 (0시간) | 3.00 |
| 1시간 전 | 2.99 |
| 1일 전 (24시간) | 2.72 |
| 2일 전 (48시간) | 2.46 |

**수량**은 그대로 곱하되 5까지만 반영한다(아래 예시는 전부 5 이하라 그대로 곱해진다).

> 아래 표의 곱셈식을 표시된 두 자리 값으로 직접 계산하면 끝자리가 1 어긋날 수 있다. 코드는 반올림 전 정밀값으로 계산하기 때문이다(예: 1시간 전은 2.99가 아니라 2.98773…).

### 검색 — 상위 상품 하나하나에 검색 비중을 준다

| 검색어 | 시간 가중치 | 비중 (1.0 × 시간) | 어느 상품에 |
|--------|-----------|-------------------|-------------|
| 사과 | 2.99 | 2.99 | P1, P4 각각 |
| 바나나 | 2.72 | 2.72 | P2 |
| 청송사과 | 2.46 | 2.46 | P1 |

결과가 0건이던 검색은 줄 상품이 없어 취향에 아무 영향을 주지 않는다.

### 장바구니

| 상품 | 이벤트 | 계산 | 비중 |
|------|--------|------|------|
| P1 | CART_ITEM_ADDED | 2 × 3 × 2.99 | 17.93 |
| P2 | CART_ITEM_ADDED | 2 × 2 × 2.72 | 10.87 |
| P3 | CART_ITEM_REMOVED | 0 × 1 × 2.46 | **0 — 완전히 제외** |

P3은 비중이 0이라 평균에서 빠질 뿐 아니라 경험 상품 목록에도 들어가지 않는다. 관심이 없어진 상품이므로 나중에 추천에 다시 나와도 된다.

### 주문

| 상품 | 이벤트 | 계산 | 비중 |
|------|--------|------|------|
| P1 | ORDER_CONFIRMED | 3 × 2 × 3.00 | 18.00 |
| P2 | ORDER_CONFIRMED | 3 × 1 × 2.72 | 8.15 |

## 상품별 합계

| 상품 | 검색 | 장바구니 | 주문 | 합계 | 전체 대비 |
|------|------|----------|------|------|-----------|
| P1 청송사과 프리미엄 1kg | 2.99 + 2.46 | 17.93 | 18.00 | **41.38** | 62.6% |
| P2 바나나 1송이 | 2.72 | 10.87 | 8.15 | **21.74** | 32.9% |
| P4 홍로사과 2kg | 2.99 | — | — | **2.99** | 4.5% |
| P3 오렌지 1kg | — | 0 | — | — | 0% |

## 벡터 합치기

상품 벡터를 비중대로 더한 뒤 길이를 1로 맞춘다(L2 정규화).

```
프로필 = 정규화( 41.38 × v(P1) + 21.74 × v(P2) + 2.99 × v(P4) )
```

비중의 합(66.11)으로 나누는 단계는 코드에 없다. 마지막에 길이를 1로 맞추기 때문에 그 전에 전체를 같은 수로 나누든 말든 방향이 같아서다. 길이를 맞추는 것은 생략할 수 없다 — 상품 벡터와 코사인 유사도로 비교하려면 같은 기준이어야 한다.

벡터가 아직 없는 상품(색인 실패 등)은 조용히 건너뛴다. 쓸 수 있는 벡터가 하나도 없으면 프로필을 저장하지 않고, 그 사용자의 개인화 추천은 빈 목록이 된다.

## 저장 결과

```json
{
  "_id": "550e8400-e29b-41d4-a716-446655440000",
  "_index": "user_profile_embeddings",
  "_source": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "userProfileVector": [0.0123456, -0.0234567, "… 총 1536개"],
    "lastUpdatedAt": "2026-01-12T12:30:00Z",
    "sourceProductIds": ["…0001", "…0002"]
  }
}
```

`sourceProductIds`는 **장바구니·주문에서 비중이 0이 아니었던** 상품이다. 개인화 추천은 이 상품들을 후보에서 뺀다. 검색으로만 만난 P4는 아직 담지도 사지도 않았으므로 여기 들어가지 않고, 추천 후보로 나올 수 있다.

### 인덱스 매핑

```json
{
  "mappings": {
    "properties": {
      "userId": { "type": "keyword" },
      "userProfileVector": { "type": "dense_vector", "dims": 1536, "index": true, "similarity": "cosine" },
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

이 예시의 프로필은 P1 쪽으로 강하게 기울어 있으므로, 추천에는 P1·P2를 뺀 나머지 중 사과·과일과 가까운 상품이 먼저 올라온다. 카테고리 보너스는 주지 않는다 — 벡터 자체가 이미 그 방향으로 기울어 있어 같은 정보를 두 번 주는 셈이기 때문이다.

> 저장 타입은 `float[]`이다(2026-09-05 상품 벡터와 통일). ES에 들어가는 JSON은 숫자 배열 그대로라 이미 만들어진 프로필 문서는 그대로 읽힌다.

## 관련 문서

- [`specs/taste-vector/taste-vector-pipeline.md`](specs/taste-vector/taste-vector-pipeline.md) — 계산 규칙·값의 SSOT
- [`specs/vector-similarity/vector-similarity-search.md`](specs/vector-similarity/vector-similarity-search.md) — 이 벡터로 후보를 뽑는 벡터 검색·다양화
- [`audits/taste-vector/001-profile-refresh-path.md`](audits/taste-vector/001-profile-refresh-path.md) — 재설계 배경과 근거
- [`product/glossary.md`](product/glossary.md) — 용어
