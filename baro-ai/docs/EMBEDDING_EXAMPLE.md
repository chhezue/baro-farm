# 📊 UserProfileEmbeddingDocument 생성 예시

## 🎯 실제 데이터 흐름 예시

### **입력 데이터 (사용자 행동 로그)**

사용자 ID: `550e8400-e29b-41d4-a716-446655440000`

#### **1. 검색 로그 (최대 5개)**
```json
[
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "searchQuery": "사과",
    "category": "과일",
    "searchedAt": "2026-01-12T10:00:00Z"  // 1시간 전
  },
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "searchQuery": "바나나",
    "category": "과일",
    "searchedAt": "2026-01-11T15:00:00Z"  // 1일 전
  },
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "searchQuery": "청송사과",
    "category": "과일",
    "searchedAt": "2026-01-10T09:00:00Z"  // 2일 전
  }
]
```

#### **2. 장바구니 로그 (최대 5개)**
```json
[
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "productId": "product-001",
    "productName": "청송사과 프리미엄 1kg",
    "eventType": "ADD",
    "quantity": 3,
    "occurredAt": "2026-01-12T11:00:00Z"  // 1시간 전
  },
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "productId": "product-002",
    "productName": "바나나 1송이",
    "eventType": "ADD",
    "quantity": 2,
    "occurredAt": "2026-01-11T16:00:00Z"  // 1일 전
  },
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "productId": "product-003",
    "productName": "오렌지 1kg",
    "eventType": "REMOVE",
    "quantity": 1,
    "occurredAt": "2026-01-10T10:00:00Z"  // 2일 전 (제거됨)
  }
]
```

#### **3. 주문 로그 (최대 5개)**
```json
[
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "productId": "product-001",
    "productName": "청송사과 프리미엄 1kg",
    "eventType": "ORDER_CREATED",
    "quantity": 2,
    "occurredAt": "2026-01-12T12:00:00Z"  // 30분 전
  },
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "productId": "product-002",
    "productName": "바나나 1송이",
    "eventType": "ORDER_CREATED",
    "quantity": 1,
    "occurredAt": "2026-01-11T17:00:00Z"  // 1일 전
  }
]
```

---

## 🔄 가중치 계산 과정

### **1단계: 검색 로그 가중치 계산**

| 검색어 | 시간 | 시간 가중치 | 반복 횟수 |
|--------|------|------------|----------|
| "사과" | 1시간 전 | 2.8 | 3번 |
| "바나나" | 1일 전 | 1.5 | 2번 |
| "청송사과" | 2일 전 | 1.2 | 1번 |

**검색어 텍스트:**
```
"사과, 사과, 사과, 바나나, 바나나, 청송사과"
```

### **2단계: 장바구니 로그 가중치 계산**

| 상품명 | 이벤트 | 수량 | 시간 | 시간 가중치 | 최종 가중치 | 반복 횟수 |
|--------|--------|------|------|------------|------------|----------|
| "청송사과 프리미엄 1kg" | ADD | 3 | 1시간 전 | 2.8 | 2 × 3 × 2.8 = 16.8 | 17번 |
| "바나나 1송이" | ADD | 2 | 1일 전 | 1.5 | 2 × 2 × 1.5 = 6.0 | 6번 |
| "오렌지 1kg" | REMOVE | 1 | 2일 전 | 1.2 | 0 × 1 × 1.2 = 0 | 0번 (제외) |

**장바구니 텍스트:**
```
"청송사과 프리미엄 1kg" × 17번
"바나나 1송이" × 6번
```

### **3단계: 주문 로그 가중치 계산**

| 상품명 | 이벤트 | 수량 | 시간 | 시간 가중치 | 최종 가중치 | 반복 횟수 |
|--------|--------|------|------|------------|------------|----------|
| "청송사과 프리미엄 1kg" | ORDER_CREATED | 2 | 30분 전 | 2.9 | 3 × 2 × 2.9 = 17.4 | 17번 |
| "바나나 1송이" | ORDER_CREATED | 1 | 1일 전 | 1.5 | 3 × 1 × 1.5 = 4.5 | 5번 |

**주문 텍스트:**
```
"청송사과 프리미엄 1kg" × 17번
"바나나 1송이" × 5번
```

---

## 📝 최종 대표 텍스트 생성

모든 텍스트를 쉼표로 구분하여 결합:

```
"사과, 사과, 사과, 바나나, 바나나, 청송사과, 청송사과 프리미엄 1kg, 청송사과 프리미엄 1kg, ..., 청송사과 프리미엄 1kg(17번), 바나나 1송이, 바나나 1송이, ..., 바나나 1송이(6번), 청송사과 프리미엄 1kg, 청송사과 프리미엄 1kg, ..., 청송사과 프리미엄 1kg(17번), 바나나 1송이, 바나나 1송이, ..., 바나나 1송이(5번)"
```

**요약:**
- 총 텍스트 길이: 약 500-600자
- 주요 키워드 반복:
  - "청송사과 프리미엄 1kg": 34번 (장바구니 17 + 주문 17)
  - "바나나 1송이": 11번 (장바구니 6 + 주문 5)
  - "사과": 3번 (검색)
  - "바나나": 2번 (검색)
  - "청송사과": 1번 (검색)

---

## 🤖 임베딩 벡터 생성

### **OpenAI Embedding API 호출**

```java
embeddingModel.embed(List.of(representativeText))
```

**입력:**
```
"사과, 사과, 사과, 바나나, 바나나, 청송사과, 청송사과 프리미엄 1kg, ..."
```

**출력:**
```java
float[] vector = [
  0.0123456f, -0.0234567f, 0.0345678f, ...,  // 1536차원
  // ... 총 1536개의 float 값
]
```

**변환:**
```java
List<Double> userProfileVector = [
  0.0123456, -0.0234567, 0.0345678, ...,  // 1536차원 Double 리스트
  // ... 총 1536개의 Double 값
]
```

---

## 💾 Elasticsearch 저장 형태

### **최종 UserProfileEmbeddingDocument**

```json
{
  "_id": "550e8400-e29b-41d4-a716-446655440000",
  "_index": "user_profile_embeddings",
  "_source": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "userProfileVector": [
      0.0123456,
      -0.0234567,
      0.0345678,
      0.0456789,
      // ... 총 1536개의 Double 값
      -0.0123456,
      0.0234567,
      -0.0345678
    ],
    "lastUpdatedAt": "2026-01-12T12:30:00Z"
  }
}
```

### **Elasticsearch 인덱스 매핑**

```json
{
  "mappings": {
    "properties": {
      "userId": {
        "type": "keyword"
      },
      "userProfileVector": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine"
      },
      "lastUpdatedAt": {
        "type": "date"
      }
    }
  }
}
```

---

## 🔍 실제 사용 예시

### **1. 사용자 프로필 벡터 조회**

```java
UserProfileEmbeddingDocument profile = 
    userProfileEmbeddingRepository.findById(userId);

List<Double> userVector = profile.getUserProfileVector();
// [0.0123456, -0.0234567, ..., -0.0345678] (1536차원)
```

### **2. 상품 벡터와 유사도 계산**

```java
// ProductDocument의 vector와 비교
float[] productVector = productDocument.getVector();

// 코사인 유사도 계산
double similarity = cosineSimilarity(userVector, productVector);
```

### **3. 개인화 추천**

```java
// 유사도가 높은 상품들을 추천
List<ProductDocument> recommendedProducts = 
    elasticsearchService.findSimilarProducts(userVector, topK=15);
```

---

## 📊 데이터 통계

### **이 예시에서의 통계**

- **총 로그 개수**: 8개 (검색 3 + 장바구니 3 + 주문 2)
- **최종 텍스트 길이**: 약 500-600자
- **임베딩 차원**: 1536차원
- **벡터 크기**: 약 12KB (1536 × 8 bytes)
- **주요 관심사**: 
  - 청송사과 프리미엄 1kg (34번 반복) ⭐⭐⭐
  - 바나나 1송이 (11번 반복) ⭐⭐
  - 사과 관련 검색 (3번 반복) ⭐

---

## 🎯 핵심 포인트

1. **가중치 적용**: 단순 반복이 아닌, 행동의 의미를 반영한 가중치 적용
2. **시간 감쇠**: 최근 행동일수록 높은 영향력
3. **이벤트 타입**: 주문 > 장바구니 추가 > 검색 순으로 중요도 반영
4. **수량 반영**: 구매 수량이 많을수록 높은 관심도
5. **벡터 저장**: 1536차원 벡터로 사용자의 취향을 수치화

이렇게 생성된 벡터는 나중에 상품 벡터와 비교하여 개인화 추천에 사용됩니다! 🚀
