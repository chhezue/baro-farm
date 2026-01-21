# 바로팜 프로젝트 구조 (MSA)

## 📦 모듈 구조 (Repository 기준)

```
baro-farm/
├── baro-auth/                    # A. 인증 모듈
│   ├── src/main/java/com/barofarm/auth/
│   │   ├── AuthApplication.java
│   │   └── auth/                 # 인증/인가 도메인, 구매자 및 회원관리 통합
│   └── build.gradle
│
├── baro-buyer/                   # B. 구매자 모듈
│   ├── src/main/java/com/barofarm/buyer/
│   │   ├── BuyerApplication.java
│   │   ├── cart/                 # 장바구니 관리
│   │   ├── product/              # 상품 관리
│   │   └── inventory/            # 재고 관리
│   └── build.gradle
│
├── baro-seller/                  # C. 판매자 모듈
│   ├── src/main/java/com/barofarm/seller/
│   │   ├── SellerApplication.java
│   │   ├── seller/               # 판매자 회원 관리
│   │   └── farm/                 # 농장 관리
│   └── build.gradle
│
├── baro-order/                   # D. 주문 모듈
│   ├── src/main/java/com/barofarm/order/
│   │   ├── OrderApplication.java
│   │   └── order/                # 주문 관리
│   └── build.gradle
│
├── baro-payment/                 # E. 결제 모듈
│   ├── src/main/java/com/barofarm/payment/
│   │   ├── PaymentApplication.java
│   │   └── payment/              # 결제 관리
│   └── build.gradle
│
├── baro-support/                 # F. 지원 모듈
│   ├── src/main/java/com/barofarm/support/
│   │   ├── SupportApplication.java
│   │   ├── delivery/             # 배송 관리
│   │   ├── notification/         # 알림 관리
│   │   ├── experience/           # 체험 프로그램 관리
│   │   ├── search/               # 검색 관리
│   │   ├── review/               # 리뷰 관리
│   │   └── deposit/              # 예치금 관리
│   └── build.gradle
│
├── baro-settlement/              # G. 정산 모듈
│   ├── src/main/java/com/barofarm/settlement/
│   │   ├── SettlementApplication.java
│   │   └── settlement/           # 정산 관리 (DaemonSet 배포)
│   └── build.gradle
│
├── baro-ai/                      # H. AI 모듈
│   ├── src/main/java/com/barofarm/ai/
│   │   ├── AiApplication.java
│   │   ├── eventLog/
│   │   ├── recommend/            # 추천 서비스
│   │   ├── review/               # 리뷰 서비스
│   │   └── season/               # 제철 서비스
│   └── build.gradle
│
├── baro-cloud/                   # I. 인프라 모듈
│   ├── gateway/                  # API Gateway
│   ├── config/                   # Config Server
│   └── eureka/                   # Service Registry
│
├── config/checkstyle/            # 코드 품질 설정
│   ├── checkstyle.xml
│   └── suppressions.xml
├── scripts/                      # 스크립트 및 Git Hooks
│   ├── pre-commit
│   └── install-hooks.sh
├── build.gradle                  # Root Gradle 설정
├── settings.gradle
└── README.md
```

## 🎯 아키텍처 특징

### 마이크로서비스 구성

- 각 모듈은 **독립 JAR + 독립 프로세스**로 실행
- 모듈 내부는 도메인별 패키지로 분리
- 모듈 간 통신은 **Gateway(8080) + Eureka + Feign**을 사용

### 배포/포트

| 모듈 | 포트 | 포함 도메인 |
|------|------|------------|
| gateway | 8080 | API Gateway |
| eureka | 8761 | Service Registry |
| config | 8888 | Config Server |
| baro-auth | 8081 | auth |
| baro-buyer | 8082 | buyer, cart, product |
| baro-seller | 8085 | seller, farm |
| baro-order | 8087 | order |
| baro-payment | 8088 | payment |
| baro-support | 8089 | delivery, notification, experience, review, deposit |
| baro-settlement | 8090 | settlement (DaemonSet 배포) |
| baro-ai | 8092 | search, recommend, review, season |

## 🔄 통신 방식

### 모듈 내부 (같은 서비스 내 호출)
```java
// baro-buyer.jar 내부
@Service
class CartService {
    @Autowired
    private ProductService productService; // 메서드 호출
}
```

### 모듈 간 (다른 서비스)
```java
// baro-order.jar → baro-buyer.jar
@FeignClient("buyer-service")
interface ProductClient {
    @GetMapping("/products/{id}")
    Product getProduct(@PathVariable Long id); // HTTP 통신
}
```

## 📊 의존성 흐름

```
Gateway (8080)
    ↓
┌───────────────────────────────────┐
│  Eureka Server (8761)             │
└───────────────────────────────────┘
    ↓
┌───────────┬───────────┬───────────┐
│ baro-auth │baro-buyer │baro-seller│
│  (8081)   │  (8082)   │  (8085)   │
└───────────┴───────────┴───────────┘
         ↓          ↓
    ┌─────────┬─────────────┐
    │baro-order│baro-support│
    │ (8087)   │   (8089)   │
    └─────────┴─────────────┘
```

## 🚀 실행 방법

```bash
# 1. Eureka Server 실행
java -jar baro-cloud/eureka/build/libs/eureka-0.0.1-SNAPSHOT.jar

# 2. Config Server 실행
java -jar baro-cloud/config/build/libs/config-0.0.1-SNAPSHOT.jar

# 3. 비즈니스 모듈 실행
java -jar baro-auth/build/libs/baro-auth-0.0.1-SNAPSHOT.jar
java -jar baro-buyer/build/libs/baro-buyer-0.0.1-SNAPSHOT.jar
java -jar baro-seller/build/libs/baro-seller-0.0.1-SNAPSHOT.jar
java -jar baro-order/build/libs/baro-order-0.0.1-SNAPSHOT.jar
java -jar baro-support/build/libs/baro-support-0.0.1-SNAPSHOT.jar

# 4. Gateway 실행
java -jar baro-cloud/gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar
```

## 🎨 참고
- API 호출은 모두 Gateway(8080)를 경유
- 서비스 등록/발견은 Eureka(8761) 기반
- 설정은 Config Server(8888)에서 관리 가능
