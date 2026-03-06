# Baro Farm - 마이크로서비스 백엔드

Spring Boot 3.5.8 + JDK 21 기반 멀티 모듈 프로젝트.  
프로젝트 구조·배포·문서는 `docs/` 및 현재 저장소 기준으로 정리되어 있습니다.

## 📦 프로젝트 구조 (MSA 구조)

> 자세한 구조는 [BARO_FARM_STRUCTURE.md](docs/BARO_FARM_STRUCTURE.md) 참고  

```
baro-farm/
├── baro-gateway/                 # API Gateway (라우팅/인증)
├── baro-user/                    # 사용자/인증 모듈 (auth, seller)
├── baro-shopping/                # 쇼핑 모듈 (cart, product, inventory)
├── baro-order/                   # 주문 모듈 (order)
├── baro-payment/                 # 결제 모듈 (payment, deposit)
├── baro-notification/            # 알림 모듈
├── baro-settlement/              # 정산 모듈
├── baro-ai/                      # AI 모듈 (search, recommend 등)
├── baro-sample/                  # 샘플/배포 테스트용
├── baro-eureka/                  # 서비스 디스커버리 (로컬/레거시)
├── baro-config/                  # Config Server (로컬/레거시)
├── baro-opa-bundle/              # OPA 정책 번들
└── baro-common/                  # 공통 라이브러리 (배포 대상 아님)
```

## 🚀 기술 스택

- **Framework**: Spring Boot 3.5.8
- **Java**: JetBrains JDK 21
- **Build Tool**: Gradle 8.14
- **Spring Cloud**: 2025.0.0
  - Netflix Eureka (Service Discovery)
  - Spring Cloud Gateway
  - Spring Cloud Config
  - OpenFeign (서비스 간 통신)
- **Database**
  - Spring Data JPA
  - MySQL 8.0
- **Cache**
  - Redis 7.2
  - Spring Data Redis
- **Message Queue**
  - Confluent Kafka 7.6.0 (Apache Kafka 호환)
  - KRaft 모드 (Zookeeper 불필요)
  - Spring for Apache Kafka
- **Code Quality**
  - Spotless 7.0.2 (Google Java Format 1.25.2)
  - Checkstyle 10.21.4

## 🛠️ 개발 환경 설정

### 1. 프로젝트 클론 후 초기 설정

```bash
# 프로젝트 클론
git clone <repository-url>
cd baro-farm-be

# Git hooks 설치 (커밋 전 자동 검사)
./scripts/install-hooks.sh
```

### 2. 빌드

```bash
./gradlew build
```

## 🔍 코드 품질 관리

### 자동 검사 (커밋 시)

Git hooks가 설치되어 있으면 커밋할 때 자동으로 검사합니다.

### 수동 검사

```bash
# 전체 검사 (포맷 + 스타일)
./gradlew lint

# 포맷 검사만
./gradlew spotlessCheck

# 스타일 검사만 (lint)
./gradlew checkstyleMain
```

### 자동 수정

```bash
# 코드 포맷 자동 수정
./gradlew format
# 또는
./gradlew spotlessApply
```

## 🏃 서비스 실행 방법

### 1️⃣ 인프라 서비스 실행 (선행 요구사항)

**Docker Compose로 한 번에 실행 (권장):**

```bash
# 모든 인프라 서비스 실행 (Redis + Kafka KRaft 모드)
docker-compose -f docker-compose.data.yml up -d

# 특정 서비스만 실행
docker-compose -f docker-compose.data.yml up -d redis   # Redis만
docker-compose -f docker-compose.data.yml up -d kafka   # Kafka만

# 중지
docker-compose -f docker-compose.data.yml down
```

**개별 실행:**

```bash
# Redis (6379)
docker run -d --name baro-redis -p 6379:6379 redis:7.2

# Kafka (9092) - KRaft 모드 (Zookeeper 불필요)
docker run -d --name baro-kafka \
  -p 9092:9092 \
  -p 9093:9093 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  confluentinc/cp-kafka:7.6.0
```

**참고:** 로컬 인프라는 `docker-compose.data.yml` 기준으로 동작합니다. 배포 환경 인프라(k8s Helm 등)는 [BARO_DEPLOYMENT_GUIDE.md](docs/BARO_DEPLOYMENT_GUIDE.md) 참고.

### 2️⃣ Spring Boot 서비스 실행

#### Gradle로 실행

```bash
# 1. Eureka Server (서비스 디스커버리, 로컬/레거시)
./gradlew :baro-eureka:bootRun

# 2. Config Server (설정 서버, 로컬/레거시)
./gradlew :baro-config:bootRun

# 3. Gateway Service (API Gateway)
./gradlew :baro-gateway:bootRun

# 4. 비즈니스 모듈 실행
./gradlew :baro-user:bootRun        # 사용자/인증 모듈 (auth, seller)
./gradlew :baro-shopping:bootRun    # 쇼핑 모듈 (cart, product, inventory)
./gradlew :baro-order:bootRun       # 주문 모듈 (order)
./gradlew :baro-payment:bootRun     # 결제 모듈 (payment)
./gradlew :baro-notification:bootRun # 알림 모듈
./gradlew :baro-settlement:bootRun  # 정산 모듈 (settlement)
./gradlew :baro-ai:bootRun          # AI 모듈 (search, recommend)
./gradlew :baro-sample:bootRun      # 샘플/배포 테스트용
```

#### JAR로 실행

```bash
# 빌드
./gradlew build

# 실행
java -jar baro-eureka/build/libs/baro-eureka-*.jar
java -jar baro-config/build/libs/baro-config-*.jar
java -jar baro-gateway/build/libs/baro-gateway-*.jar
java -jar baro-user/build/libs/baro-user-*.jar
java -jar baro-shopping/build/libs/baro-shopping-*.jar
java -jar baro-order/build/libs/baro-order-*.jar
java -jar baro-payment/build/libs/baro-payment-*.jar
java -jar baro-notification/build/libs/baro-notification-*.jar
java -jar baro-settlement/build/libs/baro-settlement-*.jar
java -jar baro-ai/build/libs/baro-ai-*.jar
java -jar baro-sample/build/libs/baro-sample-*.jar
```

## 🌐 서비스 포트 정보

| 구분 | 모듈 | 포트 | 포함 도메인 |
|------|------|------|------------|
| **인프라** | redis | 6379 | Cache Server |
| | kafka | 29092 | Message Broker (KRaft 모드) |
| | mysql | 3306 | Database |
| | elasticsearch | 9200 | Search Engine |
| **인프라(로컬)** | baro-eureka | 8761 | Service Registry |
| | baro-config | 8888 | Config Server |
| **비즈니스** | baro-gateway | 8080 | API Gateway |
| | baro-shopping | 8082 | cart, product, inventory |
| | baro-order | 8083 | order |
| | baro-user | 8087 | auth, seller |
| | baro-payment | 8088 | payment, deposit |
| | baro-sample | 8090 | 샘플/배포 테스트 |
| | baro-notification | 8091 | 알림 |
| | baro-ai | 8092 | search, recommend |
| | baro-settlement | 8093 | 정산 |

## 💾 리소스 제한 사항

> **t3.medium (4GB RAM) 2대 환경 최적화 설정**  
> 모든 서비스의 메모리 사용량을 제한하여 안정적인 운영을 보장합니다.
> - **Master Node**: MySQL, Redis, Elasticsearch, Eureka, Config, Gateway 배포
> - **Worker Node**: Kafka, 비즈니스 서비스 모듈 배포

### Spring Cloud 인프라 모듈

| 서비스 | 메모리 제한 (JVM) | Healthcheck | 비고 |
|--------|------------------|-------------|------|
| **eureka** | `-Xms128m -Xmx256m` | 60s 간격 | Service Registry |
| **config** | `-Xms256m -Xmx384m` | 60s 간격 | Config Server |
| **gateway** | `-Xms256m -Xmx384m` | 60s 간격 | API Gateway |

**설정 파일:** `docker-compose.cloud.yml`

### 비즈니스 서비스 모듈

| 서비스 | 메모리 제한 (JVM) | 의존성 | 비고 |
|--------|------------------|--------|------|
| **baro-user** | `-Xms64m -Xmx128m` | MySQL, Redis | 인증/인가, 판매자 |
| **baro-shopping** | `-Xms64m -Xmx128m` | MySQL, Kafka | 장바구니, 상품, 재고 |
| **baro-order** | `-Xms64m -Xmx128m` | MySQL, Kafka | 주문 |
| **baro-payment** | `-Xms64m -Xmx128m` | MySQL, Kafka | 결제, 예치금 |
| **baro-notification** | `-Xms64m -Xmx128m` | Kafka | 알림 |
| **baro-settlement** | `-Xms64m -Xmx128m` | MySQL, Kafka | 정산 |
| **baro-ai** | `-Xms64m -Xmx128m` | Kafka, Elasticsearch | 검색, 추천 |

### 데이터 인프라 모듈

> **배포 노드 정보:**
> - **MySQL, Redis, Elasticsearch**: Master Node에 배포
> - **Kafka**: Worker Node에 배포

#### MySQL

| 설정 | 값 | 비고 |
|------|-----|------|
| **배포 노드** | Master Node | - |
| **InnoDB 버퍼 풀** | 256M | `innodb-buffer-pool-size` |
| **최대 연결 수** | 100 | `max-connections` |
| **예상 최대 메모리** | ~400MB | InnoDB 버퍼 풀 + 프로세스 메모리 |

**설정 위치:** `docker-compose.data.yml`

```yaml
command: --innodb-buffer-pool-size=256M --max-connections=100 --lower-case-table-names=1
```

**참고:** MySQL의 실제 메모리 사용량은 InnoDB 버퍼 풀(256M)과 프로세스 오버헤드(약 100-150M)를 합쳐 약 400MB 정도입니다.

#### Redis

| 설정 | 값 | 비고 |
|------|-----|------|
| **배포 노드** | Master Node | - |
| **최대 메모리** | 256MB | `maxmemory` |
| **메모리 정책** | allkeys-lru | LRU 기반 키 제거 |
| **AOF (Append Only File)** | 활성화 | 영속성 보장 |

**설정 위치:** `docker-compose.data.yml`

```yaml
command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:-redis123} --maxmemory 256mb --maxmemory-policy allkeys-lru
```

#### Kafka

| 설정 | 값 | 비고 |
|------|-----|------|
| **배포 노드** | Worker Node | - |
| **JVM 힙 메모리** | `-Xms192m -Xmx256m` | Kafka 프로세스 메모리 |
| **Healthcheck 간격** | 60s | CPU 사용량 최적화 |
| **모드** | KRaft | Zookeeper 불필요 |

**설정 위치:** `docker-compose.kafka.yml`

```yaml
environment:
  KAFKA_HEAP_OPTS: -Xms192m -Xmx256m
init: true  # 좀비 프로세스 방지
```

#### Elasticsearch

| 설정 | 값 | 비고 |
|------|-----|------|
| **배포 노드** | Master Node | - |
| **JVM 힙 메모리** | `-Xms192m -Xmx384m` | Elasticsearch 프로세스 메모리 |
| **Healthcheck 간격** | 30s | 상태 확인 주기 |
| **Healthcheck 방식** | CMD (직접 실행) | 좀비 프로세스 방지 |
| **엔드포인트** | `/_cluster/health` | 클러스터 상태 확인 |

**설정 위치:** `docker-compose.elasticsearch.yml`

```yaml
environment:
  ES_JAVA_OPTS: -Xms192m -Xmx384m
init: true  # 좀비 프로세스 방지
healthcheck:
  test: ["CMD", "curl", "-sSf", "http://localhost:9200/_cluster/health"]
  interval: 30s
```

### 전체 메모리 사용량 요약

| 카테고리 | 서비스 | 메모리 (최대) |
|----------|--------|--------------|
| **Spring Cloud** | eureka | 300MB |
| | config | 300MB |
| | gateway | 300MB |
| | **소계** | **900MB** |
| **비즈니스 서비스** | baro-user | 350MB |
| | baro-shopping | 400MB |
| | baro-order | 400MB |
| | baro-payment | 350MB |
| | baro-notification | 350MB |
| | baro-settlement | 350MB |
| | baro-ai | 350MB |
| | **소계** | **1.95GB** |
| **데이터 인프라** | mysql | 400MB |
| | redis | 256MB |
| | kafka | 256MB |
| | elasticsearch | 600MB |
| | **소계** | **1.5GB** |
| **총합** | | **~4.35GB** |

> **참고:**
> - **Master Node (t3.medium 4GB)**: OS 및 Docker 데몬 ~1GB, 시스템 버퍼 ~0.5GB, 실제 사용 가능 ~2.5GB
> - **Worker Node (t3.medium 4GB)**: OS 및 Docker 데몬 ~1GB, 시스템 버퍼 ~0.5GB, 실제 사용 가능 ~2.5GB
> - Master Node에는 MySQL, Redis, Elasticsearch, Eureka, Config, Gateway를, Worker Node에는 Kafka와 비즈니스 서비스 모듈을 배포하여 메모리 사용량을 최적화

### 리소스 모니터링

```bash
# 실시간 리소스 사용량 확인
docker stats

# 특정 컨테이너 메모리 사용량 확인
docker stats baro-user baro-shopping baro-order

# 전체 메모리 사용량 확인
free -h
```

### 리소스 조정 가이드

메모리 부족 시 다음 순서로 조정을 고려하세요:

1. **Healthcheck 간격 증가** (CPU 사용량 감소)
   ```yaml
   healthcheck:
     interval: 60s  # 30s → 60s
   ```

2. **JVM GC 튜닝** (메모리 효율 향상)
   ```yaml
   JAVA_OPTS=-Xms64m -Xmx128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
   ```

3. **비활성 서비스 중지** (일시적)
   ```bash
   docker-compose -f docker-compose.yml stop
   ```

4. **인스턴스 타입 업그레이드** (장기적)
   - t3.medium (4GB) → t3.large (8GB) 또는 t3.xlarge (16GB)

## 🔗 주요 URL

- **Eureka Dashboard**: http://localhost:8761
- **API Gateway**: http://localhost:8080
- **Config Server**: http://localhost:8888

## 📋 API 경로

모든 API는 Gateway를 통해 접근합니다: (Port: 8080)

| 서비스 | 경로 |
|--------|------|
| User (인증/판매자) | `/user-service/**` |
| Shopping (상품/장바구니/재고) | `/shopping-service/**` |
| Order | `/order-service/**` |
| Payment | `/payment-service/**` |
| Notification | `/notification-service/**` |
| AI | `/ai-service/**` |
| Sample (테스트) | `/sample-service/**` |
| Settlement (정산) | `/settlement-service/**` (prod 라우팅은 배포 가이드 참고) |

> API·배포 검증: [BARO_POSTMAN_USER_SELLER_PRODUCT_GUIDE.md](docs/BARO_POSTMAN_USER_SELLER_PRODUCT_GUIDE.md), [BARO_DEPLOY_TEST_GUIDE.md](docs/BARO_DEPLOY_TEST_GUIDE.md)

## 📚 문서

| 문서 | 설명 |
|------|------|
| [BARO_FARM_STRUCTURE.md](docs/BARO_FARM_STRUCTURE.md) | 프로젝트·모듈 구조 상세 |
| [BARO_FARM_OVERVIEW.md](docs/BARO_FARM_OVERVIEW.md) | 프로젝트 개요 |
| [BARO_FARM_PLAN.md](docs/BARO_FARM_PLAN.md) | 프로젝트 계획서 |
| [BARO_FARM_USER_STORIES.md](docs/BARO_FARM_USER_STORIES.md) | 사용자 스토리 |
| [BARO_DEPLOYMENT_GUIDE.md](docs/BARO_DEPLOYMENT_GUIDE.md) | **현재 배포 구조** (팀/접근자용) |
| [BARO_DEPLOYMENT_HISTORY.md](docs/BARO_DEPLOYMENT_HISTORY.md) | 배포 아키텍처 변경 이력 (V1→V2→V3) |
| [CICD_GUIDE.md](docs/CICD_GUIDE.md) | CI/CD 설정 및 트러블슈팅 |
| [BRANCH_AND_MODULE_STRATEGY.md](docs/BRANCH_AND_MODULE_STRATEGY.md) | 브랜치·모듈 전략 |
| [OPA_LOCAL_SETUP.md](docs/OPA_LOCAL_SETUP.md) | OPA 로컬 설정 |
| [OPA_LOCAL_DOCKER_GUIDE.md](docs/OPA_LOCAL_DOCKER_GUIDE.md) | OPA 로컬 Docker |
| [OPA_ARCHITECTURE.md](docs/OPA_ARCHITECTURE.md) | OPA 아키텍처 |
| [OPA_LOCAL_FLOW.md](docs/OPA_LOCAL_FLOW.md) | OPA 로컬 플로우 |

## 🔒 인증

Gateway의 `AuthenticationFilter`에서 JWT 토큰을 검증합니다.
인증이 필요한 API 호출 시 `Authorization: Bearer {token}` 헤더가 필요합니다.

## 🌿 브랜치 전략

### 브랜치 구조

```
main                          # 최종 배포 (Production)
 │
 ├── main-gateway              # Gateway 안정 버전
 ├── main-user                 # User(인증/회원/판매자) 안정 버전
 ├── main-shopping             # Shopping(상품/재고/장바구니) 안정 버전
 ├── main-order                # Order 안정 버전
 ├── main-payment              # Payment 안정 버전
 ├── main-notification        # Notification 안정 버전
 ├── main-settlement          # Settlement 안정 버전
 ├── main-ai                  # AI 안정 버전
 └── ...
      │
      ├── dev-user
      ├── dev-shopping
      ├── dev-order
      └── ...
           │
           └── feature/...    # 기능 개발 브랜치 (예: feature/issue-107-2nd-deploy)
```

### 브랜치 네이밍 규칙

> **💡 브랜치명은 영문으로, 커밋 메시지는 한글로 작성합니다.**  
| 브랜치 | 용도 | 예시 |
|--------|------|------|
| `main` | 최종 배포 버전 | - |
| `main-{모듈}` | 모듈별 안정 버전 | `main-user`, `main-order` |
| `dev-{모듈}` | 모듈별 개발 통합 | `dev-user`, `dev-order` |
| `feature/issue-{이슈번호}-{기능설명-영문}` | 기능 개발 | `feature/issue-123-add-cart-item` |
| `fix/issue-{이슈번호}-{버그설명-영문}` | 버그 수정 | `fix/issue-456-product-search-error` |
| `hotfix/issue-{이슈번호}-{긴급수정-영문}` | 긴급 버그 수정 | `hotfix/issue-789-payment-failure` |

### 작업 흐름

```bash
# 1. GitHub에서 이슈 생성 (예: #123 장바구니 담기 기능)

# 2. dev 브랜치에서 feature 브랜치 생성
git checkout dev-shopping
git checkout -b feature/issue-123-add-cart-item

# 3. 작업 후 커밋 (커밋 메시지는 한글 사용)
git add .
git commit -m "[Feat] #123 - 장바구니 담기 기능 추가"

# 4. dev 브랜치로 머지
git checkout dev-shopping
git merge feature/issue-123-add-cart-item

# 5. 테스트 후 main 브랜치로 머지
git checkout main-shopping
git merge dev-shopping
```

### 커밋 메시지 규칙

```
[타입] #이슈번호 - 설명

예시:
[Feat] #123 - 회원가입 기능 추가
[Fix] #456 - 수량 변경 버그 수정
[Refactor] #789 - 상품 조회 로직 개선
[Docs] #321 - README 브랜치 전략 추가
```

| 타입 | 설명 |
|------|------|
| `Feat` | 새로운 기능 추가 |
| `Fix` | 버그 수정, 파일 등 삭제 |
| `Docs` | 문서 수정 |
| `Refactor` | 코드 리팩토링 |
| `Test` | 테스트 코드, 리팩토링 테스트 코드 추가 |
| `Chore` | 패키지 매니저 수정, 그 외 기타 수정 (ex: .gitignore) |

## 🚀 CI/CD

### 개요

- **클러스터**: k3s (EC2 기반), 앱 네임스페이스 `default`
- **이미지**: AWS ECR (`299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/*`)
- **트리거**: 앱 모듈은 **path 기반** (해당 모듈/경로 변경 시), 인프라는 **main** 브랜치 + path 또는 `workflow_dispatch`
- **배포**: `kubectl apply -f k8s/apps/common/ k8s/apps/baro-<module>/ -n default`, 이미지 ECR 푸시 후 반영

### k8s 디렉터리 구조 (현재)

```
k8s/
├── apps/
│   ├── common/               # infra-configmap.yml (infra-endpoints-config)
│   ├── baro-gateway/         # configmap, deployment, service, ingress
│   ├── baro-sample/
│   ├── baro-user/
│   ├── baro-shopping/
│   ├── baro-order/
│   ├── baro-payment/
│   ├── baro-notification/
│   ├── baro-settlement/      # + cronjob.yml
│   └── baro-ai/
└── infra/                    # Helm / Strimzi (mysql, redis, kafka, elasticsearch)
```

### GitHub Actions 요약

- **앱 워크플로**: `baro-<module>-deploy.yml` — path 변경 시 빌드 → ECR 푸시 → k3s 배포
- **인프라 워크플로**: `redis-deploy.yml`, `mysql-deploy.yml`, `kafka-deploy.yml`, `elasticsearch-deploy.yml` (main + path 또는 수동)

### 주요 Secrets (참고)

| Secret | 용도 |
|--------|------|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | ECR·AWS 접근 |
| `KUBE_CONFIG` | k3s 클러스터 접근 (base64 인코딩) |
| `GATEWAY_JWT_SECRET`, `REDIS_PASSWORD` | Gateway·Redis |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_APP_PASSWORD` | MySQL (인프라·앱) |
| `TOSS_SECRET_KEY` | 결제 모듈 (선택) |

### 롤백

```bash
kubectl rollout undo deployment/baro-<module> -n default
# 또는 이미지 태그 변경 후 kubectl apply -f k8s/apps/baro-<module>/ -n default
```

**📚 상세:** [BARO_DEPLOYMENT_GUIDE.md](docs/BARO_DEPLOYMENT_GUIDE.md), [CICD_GUIDE.md](docs/CICD_GUIDE.md)

## 📝 라이선스
