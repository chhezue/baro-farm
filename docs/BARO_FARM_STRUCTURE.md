# 바로팜 프로젝트 구조 (MSA)

## 📦 모듈 구조 (Repository 기준)

현재 프로젝트는 **도메인 기반 모듈** 구조를 사용합니다. 상세 포트는 프로젝트 루트 [README.md](../README.md)의 서비스 포트 정보를 참고하세요.

```
baro-farm/
├── baro-gateway/                 # API Gateway (라우팅/인증)
├── baro-user/                    # 사용자/인증 (auth, seller)
├── baro-shopping/                # 쇼핑 (cart, product, inventory)
├── baro-order/                   # 주문 (order)
├── baro-payment/                 # 결제 (payment, deposit)
├── baro-notification/            # 알림
├── baro-settlement/              # 정산
├── baro-ai/                      # AI (search, recommend 등)
├── baro-sample/                  # 샘플/배포 테스트용
├── baro-eureka/                  # 서비스 디스커버리 (로컬/레거시)
├── baro-config/                  # Config Server (로컬/레거시)
├── baro-opa-bundle/              # OPA 정책 번들
├── baro-common/                  # 공통 라이브러리 (배포 대상 아님)
├── config/checkstyle/
├── scripts/
├── build.gradle
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
| baro-gateway | 8080 | API Gateway |
| baro-shopping | 8082 | cart, product, inventory |
| baro-order | 8083 | order |
| baro-user | 8087 | auth, seller |
| baro-payment | 8088 | payment, deposit |
| baro-sample | 8090 | 샘플/배포 테스트 |
| baro-notification | 8091 | 알림 |
| baro-ai | 8092 | search, recommend |
| baro-settlement | 8093 | 정산 |
| baro-eureka | 8761 | Service Registry (로컬) |
| baro-config | 8888 | Config Server (로컬) |

## 🔄 통신 방식

### 모듈 내부 (같은 서비스 내 호출)
```java
// baro-shopping 내부
@Service
class CartService {
    @Autowired
    private ProductService productService; // 메서드 호출
}
```

### 모듈 간 (다른 서비스)
```java
// baro-order → baro-shopping
@FeignClient("baro-shopping")
interface ProductClient {
    @GetMapping("/products/{id}")
    Product getProduct(@PathVariable Long id); // HTTP 통신
}
```

## 📊 의존성 흐름

```
baro-gateway (8080)
    ↓
┌─────────────────────────────────────────────────────────┐
│  baro-user │ baro-shopping │ baro-order │ baro-payment  │
│  (8087)    │   (8082)      │  (8083)    │   (8088)      │
├────────────┼───────────────┼────────────┼───────────────┤
│ baro-notification │ baro-settlement │ baro-ai           │
│    (8091)         │    (8093)       │   (8092)          │
└────────────┴───────────────┴────────────┴────────────────┘
```

K8s 배포 시에는 Eureka/Config를 사용하지 않고 Gateway가 Service DNS로 직접 라우팅합니다.

## 🚀 실행 방법

```bash
# 1. (선택) Eureka / Config (로컬 개발 시)
./gradlew :baro-eureka:bootRun
./gradlew :baro-config:bootRun

# 2. Gateway 실행
./gradlew :baro-gateway:bootRun

# 3. 비즈니스 모듈 실행
./gradlew :baro-user:bootRun
./gradlew :baro-shopping:bootRun
./gradlew :baro-order:bootRun
./gradlew :baro-payment:bootRun
./gradlew :baro-notification:bootRun
./gradlew :baro-settlement:bootRun
./gradlew :baro-ai:bootRun
./gradlew :baro-sample:bootRun
```

## 🎨 참고
- API 호출은 모두 Gateway(8080)를 경유하며 `/user-service/**`, `/shopping-service/**` 등으로 라우팅
- K8s 배포 시에는 Eureka/Config 미사용, Service DNS 직접 사용
