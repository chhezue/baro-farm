# 바로팜 Spring AI 구현 가이드

## 개요

바로팜 프로젝트에서 Spring AI를 활용한 주요 기능 구현 방안입니다.
각 기능별로 필요한 기술 스택과 관련 서비스를 분석하여 정리하였습니다.

---

## 1️⃣ 이미지 생성 기능

### 기능 설명
**체험 예약이나 상품 등록 시 제목/설명을 바탕으로 이미지를 자동 생성**

- 상품 등록 시: 상품명 + 설명 → 대표 이미지 생성
- 체험 예약 등록 시: 체험 제목 + 설명 → 홍보 이미지 생성
- 관리자 승인 전 프리뷰 제공 및 재생성 기능 지원

### Spring AI 기술 스택
- `ImageModel` (Text-to-Image)
- `PromptTemplate`

### 백엔드 기술 스택
- 비동기 처리: Spring Async / Kafka
- 스토리지: AWS S3 + CloudFront
- 이미지 처리: Thumbnailator

### 추천 MCP 툴

**Stable Diffusion / Civit AI**
- **근거**: 한국어 텍스트 기반 이미지 생성에 강력하며, 농산물 이미지 생성에 적합
- **장점**: 로컬 배포 가능, 커스터마이징 용이, 한국어 프롬프트 지원 우수
- **활용 사례**: 상품 사진, 체험 프로그램 홍보 이미지 자동 생성

**Midjourney (API)**
- **근거**: 고품질 이미지 생성, 농산물 테마에 적합한 아트 스타일
- **장점**: 빠른 생성 속도, 다양한 스타일 지원
- **활용 사례**: 예약 체험의 예쁜 홍보 이미지 생성

### 관련 서비스 분석

**필요한 서비스: `product-service`, `experience-service`**

**근거:**
- `product-service`: 상품 등록 로직과 이미지 관리 담당
- `experience-service`: 체험 프로그램 등록과 이미지 관리 담당
- 현재 프로젝트에서 상품과 체험은 별도 서비스로 분리되어 있어 각 서비스에 AI 이미지 생성 기능 통합 필요

---

## 2️⃣ 추천 시스템

### 2.1 상품/예약 상세 페이지 추천

#### 기능 설명
**"함께 보면 좋은 상품" 추천**

- 상품 상세 페이지에서 유사 상품 추천
- 예약 상세 페이지에서 관련 체험 추천
- 추천 이유 함께 제공 (Explainable AI)

#### Spring AI 기술 스택
- `EmbeddingModel`
- `VectorStore`
- `Retriever`
- `ChatModel` (추천 이유 설명용)

#### 백엔드 기술 스택
- 벡터 저장소: Elasticsearch (현재 사용 중) / PGVector
- 캐싱: Redis
- 이벤트 처리: Kafka

#### 추천 MCP 툴

**OpenAI text-embedding-ada-002**
- **근거**: 상품 유사도 계산에 최적화된 임베딩 모델
- **장점**: 한국어 지원 우수, 벡터 차원이 1536으로 적절
- **활용 사례**: 상품 카테고리, 설명, 태그 기반 유사 상품 검색

**Hugging Face sentence-transformers**
- **근거**: 오픈소스 모델로 로컬 배포 가능, 다국어 지원
- **장점**: 비용 절감, 커스터마이징 가능
- **활용 사례**: 농산물 특성(신선도, 원산지 등) 반영한 임베딩

#### 관련 서비스 분석

**필요한 서비스: `product-service`, `experience-service`, `recommendation-service` (신규)**

**근거:**
- `product-service`: 상품 데이터와 메타데이터 관리
- `experience-service`: 체험 데이터 관리
- 현재 프로젝트 구조에서 추천은 별도 서비스로 분리하는 것이 적절 (마이크로서비스 아키텍처 유지)
- 상품/체험 상세 조회 시 추천 API 호출 구조

### 2.2 장바구니 기반 레시피 추천

#### 기능 설명
**"지금 담은 상품(2개 이상)으로는 ~를 만들 수 있어요"**

- 장바구니 상품 분석 → 가능한 레시피 추천
- 부족한 재료 안내
- 실제 재고 확인 후 추천

#### Spring AI 기술 스택
- `EmbeddingModel`
- `VectorStore`
- `Retriever`
- `Tool Calling` (재고 확인용)

#### 백엔드 기술 스택
- 레시피 데이터베이스: MySQL (레시피 테이블 추가)
- 도메인 서비스: 재고 확인 서비스
- 이벤트 스트리밍: Kafka

#### 추천 MCP 툴

**OpenAI GPT-4 with Tool Calling**
- **근거**: 복잡한 레시피 로직과 재고 확인이 필요한 경우에 최적
- **장점**: Function calling으로 실시간 재고 조회, 자연어 레시피 설명
- **활용 사례**: "계란, 양파, 당근 있으면 무슨 요리 가능?" 같은 복합 쿼리 처리

**Claude 3 (Anthropic)**
- **근거**: 긴 컨텍스트 처리에 강력, 레시피 상세 설명에 적합
- **장점**: 안전한 응답 생성, 한국어 이해도 높음
- **활용 사례**: 상세한 조리법 안내와 대체 재료 제안

#### 관련 서비스 분석

**필요한 서비스: `cart-service`, `product-service`, `recommendation-service`**

**근거:**
- `cart-service`: 장바구니 데이터 관리 (현재 AI 담당 서비스)
- `product-service`: 상품 재고 정보 제공
- 추천 로직은 별도 `recommendation-service`에서 처리하는 것이 확장성 좋음
- Tool Calling으로 `product-service`의 재고 확인 API 호출

### 2.3 검색어 기록 기반 개인화 추천

#### 기능 설명
**사용자의 검색어 히스토리를 분석하여 맞춤 상품/레시피 추천**

- 검색 패턴 분석 → 취향 임베딩 생성
- 개인화된 상품과 레시피 추천
- 추천 이유 설명

#### Spring AI 기술 스택
- `EmbeddingModel`
- `VectorStore`
- `ChatModel` (결과 설명용)

#### 백엔드 기술 스택
- 사용자 데이터: MySQL
- 검색 로그: Kafka / Elasticsearch
- 프로파일링: 배치 처리

#### 추천 MCP 툴

**OpenAI text-embedding-ada-002**
- **근거**: 사용자 검색 패턴을 벡터화하여 개인화 프로필 생성에 최적
- **장점**: 일관된 임베딩 차원, 검색어 의미 파악에 강력
- **활용 사례**: "유기농 채소" 검색 사용자에게 유기농 상품 우선 추천

**Cohere Embed**
- **근거**: 검색 의도 이해에 특화된 임베딩 모델
- **장점**: 검색 컨텍스트 파악에 강력, 다국어 지원
- **활용 사례**: "저녁 식사용 채소" 검색 시 저녁 식사 관련 레시피 추천

#### 관련 서비스 분석

**필요한 서비스: `search-service`, `member-service`, `recommendation-service`**

**근거:**
- `search-service`: 검색 로그 수집 (현재 AI 담당 서비스)
- `member-service`: 사용자 프로필 관리
- 추천 엔진은 `recommendation-service`에서 통합 관리
- 사용자 검색 히스토리는 `search-service`에서 수집하여 `recommendation-service`로 전달

### 2.4 장바구니/검색어 기반 랭킹 시스템

#### 기능 설명
**사용자 행동 데이터를 분석한 인기 상품/레시피 랭킹**

- 실시간 인기 상품 분석
- 트렌드 기반 랭킹 생성
- 관리자용 트렌드 리포트

#### Spring AI 기술 스택
- `ChatModel` (트렌드 분석 및 리포트 생성용)

#### 백엔드 기술 스택
- 이벤트 처리: Kafka
- 집계 처리: Kafka Streams / 배치
- 랭킹 알고리즘: Count + Weight + Time decay

#### 추천 MCP 툴

**GPT-3.5-turbo (경제적 선택)**
- **근거**: 트렌드 분석 리포트 생성에 충분한 성능, 비용 효율적
- **장점**: 빠른 응답 속도, 구조화된 리포트 생성
- **활용 사례**: "이번 주 채소 판매 트렌드: 시금치 30% 증가, 사과 15% 감소"

**Claude 3 Haiku**
- **근거**: 가성비 좋은 모델로 분석 리포트 생성에 적합
- **장점**: 저비용 고성능, 한국어 리포트 작성에 강력
- **활용 사례**: 판매자용 월간 트렌드 분석 및 예측 리포트

#### 관련 서비스 분석

**필요한 서비스: `analytics-service` (신규), `search-service`, `cart-service`**

**근거:**
- 현재 프로젝트에 `analytics-service`가 없음 (다음 단계 확장 기능)
- `search-service`: 검색 이벤트 수집
- `cart-service`: 장바구니 이벤트 수집
- 통계/분석은 별도 서비스로 분리하는 것이 마이크로서비스 원칙에 맞음

---

## 3️⃣ 챗봇 AI 기능

### 기능 설명
**냉장고 재료 입력 → 레시피 추천**

- 자연어로 재료 입력
- 대화형 레시피 추천
- 재고 확인 및 알러지 고려
- 조리법 단계별 안내

### Spring AI 기술 스택
- `ChatModel`
- `ChatMemory`
- `EmbeddingModel`
- `VectorStore` (레시피 검색용)
- `Tool Calling`

### 백엔드 기술 스택
- 대화 상태 관리: Redis
- 세션 관리: Spring Session
- API 게이트웨이: Spring Cloud Gateway

### 추천 MCP 툴

**GPT-4 with Tool Calling**
- **근거**: 복합 쿼리 처리와 실시간 재고 조회에 최적
- **장점**: Function calling으로 상품 재고 확인, 대화 맥락 유지
- **활용 사례**: "계란, 양파 있어요. 저녁 메뉴 추천해주세요" → 재고 확인 후 추천

**Claude 3 Opus**
- **근거**: 긴 대화 컨텍스트 처리에 강력, 안전한 응답 생성
- **장점**: 자연스러운 한국어 응답, 복잡한 레시피 설명
- **활용 사례**: 단계별 조리법 안내, 알러지 고려한 대체 재료 제안

### 관련 서비스 분석

**필요한 서비스: `chatbot-service` (신규), `product-service`, `recommendation-service`**

**근거:**
- 챗봇은 복잡한 비즈니스 로직이므로 별도 서비스로 분리
- `product-service`: 재고 및 상품 정보 조회 (Tool Calling)
- `recommendation-service`: 레시피 추천 로직 활용
- 현재 프로젝트에서 챗봇은 확장 기능으로 계획되어 있지만 AI 핵심 기능

---

## 4️⃣ 판매자 대시보드 AI 활용

### 기능 설명
**판매자용 AI 지원 기능**

- 상품 판매 트렌드 분석 및 예측
- 가격 최적화 제안
- 재고 관리 자동화
- 고객 피드백 요약

### Spring AI 기술 스택
- `ChatModel` (분석 리포트 생성)
- `EmbeddingModel` (트렌드 분석용)

### 백엔드 기술 스택
- 데이터 분석: Elasticsearch aggregation
- 배치 처리: Spring Batch
- 실시간 대시보드: WebSocket

### 추천 MCP 툴

**GPT-4 with Data Analysis**
- **근거**: 판매 데이터 분석과 인사이트 생성에 특화
- **장점**: 복잡한 데이터 패턴 파악, 실행 가능한 제안 생성
- **활용 사례**: "가격 인상 제안: A상품 현재 15% 마진, 5% 인상 시 마진 18%로 개선 가능"

**Claude 3 with Business Intelligence**
- **근거**: 비즈니스 분석에 강력, 한국어 리포트 작성에 적합
- **장점**: 안전한 분석 결과, 실질적인 비즈니스 인사이트
- **활용 사례**: 재고 최적화 제안, 고객 피드백 기반 상품 개선 방안

### 관련 서비스 분석

**필요한 서비스: `analytics-service`, `seller-service`, `product-service`**

**근거:**
- `seller-service`: 판매자 전용 기능 (현재 프로젝트에 존재)
- `analytics-service`: 데이터 분석 및 AI 리포트 생성
- `product-service`: 상품 판매 데이터 제공
- 판매자 대시보드는 `seller-service`에 통합하되 분석은 `analytics-service`에서 처리

---

## 5️⃣ 리뷰 AI 요약 기능

### 기능 설명
**상품 리뷰 데이터를 AI로 분석 및 요약**

- 긍정/부정 리뷰 분류
- 주요 키워드 추출
- 판매자용 인사이트 제공
- 고객용 리뷰 하이라이트

### Spring AI 기술 스택
- `ChatModel` (요약 생성)
- `EmbeddingModel` (리뷰 클러스터링)

### 백엔드 기술 스택
- 리뷰 데이터: MySQL
- 텍스트 처리: Elasticsearch
- 배치 분석: Spring Batch

### 추천 MCP 툴

**GPT-4 with Sentiment Analysis**
- **근거**: 감정 분석과 텍스트 요약에 최적화된 모델
- **장점**: 한국어 감정 표현 파악에 강력, 맥락 이해 우수
- **활용 사례**: "신선도 만족 85%, 맛 만족 78%, 배송 불만 12%" 요약

**Claude 3 with Text Analytics**
- **근거**: 긴 텍스트 분석과 인사이트 추출에 강력
- **장점**: 안전한 분석 결과, 상세한 피드백 카테고리화
- **활용 사례**: 고객 리뷰 클러스터링 및 주요 불만사항 추출

### 관련 서비스 분석

**필요한 서비스: `review-service`, `analytics-service`**

**근거:**
- `review-service`: 리뷰 데이터 관리 (현재 프로젝트에 존재)
- `analytics-service`: AI 기반 분석 및 요약 생성
- 리뷰 분석은 리뷰 서비스와 분석 서비스의 협업으로 구현

---

## 📋 MCP 툴 선택 전략 요약

### 1️⃣ 모델 선택 우선순위
1. **OpenAI GPT-4**: 복잡한 로직, Tool Calling, 고품질 응답 필요 시
2. **Claude 3**: 안전성, 한국어 처리, 긴 컨텍스트 필요 시
3. **GPT-3.5-turbo**: 비용 효율적, 간단한 분석/요약 작업 시
4. **오픈소스 모델**: 로컬 배포, 비용 절감, 커스터마이징 필요 시

### 2️⃣ 기능별 추천 모델 매핑
- **이미지 생성**: Stable Diffusion, Midjourney
- **임베딩/유사도**: OpenAI text-embedding-ada-002
- **챗봇/대화**: GPT-4, Claude 3 Opus
- **추천 이유 설명**: Claude 3, GPT-4
- **분석/리포트**: Claude 3, GPT-3.5-turbo
- **요약/감정분석**: GPT-4, Claude 3

### 3️⃣ 구현 시 고려사항
- **비용**: GPT-3.5-turbo로 시작해서 필요시 업그레이드
- **성능**: Claude 3는 안전하고 일관된 응답
- **한국어**: Claude 3와 GPT-4가 가장 우수
- **Tool Calling**: 복잡한 비즈니스 로직 연동 시 필수

### 4️⃣ 라이브 코딩 활용 팁
- **MCP 플랫폼**: Replicate, Hugging Face, OpenAI API 우선 활용
- **테스트**: 각 기능별로 단위 테스트 작성하며 점진적 적용
- **모니터링**: 응답 시간, 비용, 정확도 지표 추적
- **폴백**: AI 실패 시 기본 로직으로 graceful degradation

---

## 🛠️ OpenAI API + AWS 유료 버전 활용 전략

### 💡 보유 리소스 기반 우선순위

#### 1️⃣ **즉시 사용 가능한 조합 (OpenAI API + AWS 기본 서비스)**
- **이미지 생성**: OpenAI DALL-E 3 + AWS S3
- **챗봇**: OpenAI GPT-4 + AWS Lambda (Tool Calling)
- **추천**: OpenAI Embeddings + AWS OpenSearch
- **리뷰 분석**: OpenAI GPT-4 + AWS Comprehend

#### 2️⃣ **고급 활용 (AWS 유료 서비스 연동)**
- **개인화 추천**: OpenAI Embeddings + AWS Personalize
- **예측 분석**: OpenAI GPT-4 + AWS SageMaker
- **대화 관리**: OpenAI GPT-4 + AWS Lex
- **비즈니스 인텔리전스**: OpenAI GPT-4 + AWS QuickSight

### 🔧 **구현 시 참고사항**

#### OpenAI API 설정
```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        model: gpt-4
      embedding:
        model: text-embedding-ada-002
```

#### AWS 서비스 설정
```yaml
aws:
  region: ap-northeast-2
  bedrock:
    enabled: true
  comprehend:
    enabled: true
  personalize:
    enabled: true
```

#### 비용 최적화 팁
- **모델 선택**: GPT-3.5-turbo로 시작 → 필요시 GPT-4 업그레이드
- **캐싱**: 자주 사용되는 응답 Redis 캐시
- **배치 처리**: 실시간이 아닌 배치로 AI 처리
- **모니터링**: AWS Cost Explorer로 비용 추적

### 🎯 **권장 구현 순서**

1. **Week 1**: OpenAI API + 기본 AWS 서비스 연동 테스트
2. **Week 2**: 추천 시스템 (Embeddings + OpenSearch)
3. **Week 3**: 챗봇 (GPT-4 + Lambda)
4. **Week 4**: 고급 기능 (Personalize, SageMaker) 추가

### 📊 **예상 비용 (월간)**
- **OpenAI API**: $50-200 (요청량에 따라)
- **AWS AI/ML**: $20-100 (SageMaker, Bedrock 등)
- **총합**: $70-300 (스타트업 규모에 적합)

이 조합으로 바로팜 프로젝트에서 **고품질 AI 기능**을 **합리적인 비용**으로 구현할 수 있습니다! 🚀