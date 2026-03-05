# 바로팜(Baro-Farm) 세미 프로젝트 계획서

> **프로젝트명**: 바로팜 (Baro-Farm)  
> **프로젝트 기간**: 2025년 12월 1일 ~ 12월 17일 (16일, 약 2.5주)  
> **프로젝트 목표**: 핵심 MVP 완성 및 시연 가능한 수준 구현  
> **팀 구성**: 4명 (아키텍트, 백엔드1, 백엔드2, AI)

---

## 📋 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [프로젝트 목표 및 범위](#2-프로젝트-목표-및-범위)
3. [기술 스택 및 아키텍처](#3-기술-스택-및-아키텍처)
4. [프로젝트 일정](#4-프로젝트-일정)
5. [팀 역할 및 책임](#5-팀-역할-및-책임)
6. [CI/CD 파이프라인](#6-cicd-파이프라인)
7. [배포 전략](#7-배포-전략)
8. [리스크 관리](#8-리스크-관리)
9. [성공 기준](#9-성공-기준)
10. [문서화](#10-문서화)
11. [다음 단계](#11-다음-단계-최종)

---

## 1. 프로젝트 개요

### 1.1 프로젝트 비전

**바로팜(Baro-Farm)**은 신선한 농산물을 생산자로부터 소비자에게 직접 연결하는 **Farm-to-Table 이커머스 플랫폼**입니다. 도시 소비자와 농촌 생산자를 연결하여 신뢰 기반의 직거래를 실현하고, 농장 체험 서비스를 통해 지속가능한 농업 생태계를 구축합니다.

### 1.2 핵심 가치

- **신선함**: 생산자로부터 직접 배송되는 신선한 농산물
- **신뢰**: 투명한 생산 정보와 리뷰 시스템
- **지속가능성**: 로컬푸드 운동을 통한 환경 보호
- **연결**: 도시와 농촌을 잇는 디지털 플랫폼

### 1.3 주요 기능

#### 핵심 기능 (MVP)
- ✅ 회원 등록 및 관리
- ✅ 사용자 인증/인가 (JWT 기반)
- ✅ 농장 등록 및 관리
- ✅ 상품 등록 및 관리
- ✅ 리뷰 및 평점 시스템
- ✅ 주문 및 결제 처리
- ✅ 정산 관리
- ✅ 배송 관리
- ✅ 상품 검색 (Elasticsearch)
- ✅ 장바구니 기능
- ✅ 농장 체험 예약
- ✅ 알림 시스템
- ✅ 검색

#### 향후 확장 기능
- 구독 시스템 (밀키트 등 정기 배송)
- 쿠폰 관리
- 통계
- AI 기반 추천 시스템

---

## 2. 프로젝트 목표 및 범위

### 2.1 프로젝트 목표

#### 단기 목표 (MVP)
- [x] 마이크로서비스 아키텍처 기반 시스템 구축
- [x] 핵심 비즈니스 로직 구현 (주문 → 결제 → 배송, 정산)
- [x] 이벤트 기반 아키텍처 (Kafka) 구현 (알림, 주문, 예약, 결제)
- [x] CI/CD 파이프라인 구축 (Github Actions)
- [x] Kubernetes 기반 배포 환경 구축 (세미 이후로 도입 예정)
- [x] 시연 가능한 수준의 완성도 달성

#### 장기 목표 (Phase 2+)
- 확장 기능 추가 (AI 추천, 쿠폰, 구독 서비스)
- 모니터링 및 로깅 시스템 고도화
- 성능 최적화 및 확장성 개선

### 2.2 프로젝트 범위

#### 포함 범위 (In-Scope)
- **핵심 마이크로서비스 (도메인 기반 모듈)**
  - **baro-gateway**: API 게이트웨이 (라우팅/인증)
  - **baro-user**: 회원·인증·판매자 (auth, seller)
  - **baro-shopping**: 상품·재고·장바구니 (product, inventory, cart)
  - **baro-order**: 주문·리뷰
  - **baro-payment**: 결제·예치금
  - **baro-notification**: 알림
  - **baro-settlement**: 정산
  - **baro-ai**: 검색·추천 등 AI
  - **baro-sample**: 샘플/배포 테스트용
  - **baro-eureka**, **baro-config**: 서비스 디스커버리·설정 (로컬/레거시)
  - **baro-opa-bundle**: OPA 정책 번들

- **인프라 및 DevOps**
  - Docker 컨테이너화
  - GitHub Actions CI/CD (path 기반 또는 브랜치 기반 트리거)
  - AWS ECR (컨테이너 레지스트리)
  - k3s(Kubernetes) 오케스트레이션, ALB + Ingress

- **데이터베이스**
  - MySQL (서비스별 스키마 분리)
  - Redis (캐싱/세션)
  - Elasticsearch (검색)

- **프론트엔드**
  - Vercel AI를 활용한 시연 화면 개발

#### 제외 범위 (Out-of-Scope)
- 실제 택배사 API 연동 (Mock 사용), 세미 이후에 적용

---

## 3. 기술 스택 및 아키텍처

### 3.1 기술 스택

#### 백엔드
- **언어**: Java 21 (LTS)
- **프레임워크**: Spring Boot 3.5.x, Spring Cloud 2025.x
- **데이터베이스**: MySQL 8, Redis 7, Elasticsearch
- **메시징**: Apache Kafka (KRaft 모드)
- **빌드 도구**: Gradle 8.x

#### 인프라 및 DevOps
- **컨테이너**: Docker
- **오케스트레이션**: Kubernetes (k3s)
- **CI/CD**: GitHub Actions (path 기반·브랜치 기반)
- **컨테이너 레지스트리**: AWS ECR (baro-prod/*)
- **모니터링**: Kibana (향후)

### 3.2 아키텍처

#### 마이크로서비스 아키텍처 (MSA)
```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ baro-gateway    │  (API Gateway, 라우팅/인증)
└──────┬──────────┘
       │
       ├──► baro-user         (인증/회원/판매자)
       ├──► baro-shopping     (상품/재고/장바구니)
       ├──► baro-order        (주문/리뷰)
       ├──► baro-payment      (결제/예치금)
       ├──► baro-notification (알림)
       ├──► baro-settlement    (정산)
       ├──► baro-ai           (검색/추천)
       └──► baro-sample       (샘플/테스트)
```

#### 이벤트 기반 아키텍처 (Kafka)
```
baro-order ──► Kafka ──► baro-payment
                      └─► baro-notification
                      └─► baro-settlement
```

```
baro-shopping ──► Kafka ──► baro-ai (검색 인덱스 등)
```

```
baro-user, baro-order 등 ──► Kafka ──► baro-notification
```

```
baro-settlement ──► 배치/CronJob ──► 정산 처리
```

### 3.3 데이터베이스 전략

- **서비스별 독립 데이터베이스**: 각 마이크로서비스는 MySql 서버 내 각 데이터베이스 연동
- **CQRS 패턴**: 읽기/쓰기 분리 (검색 서비스는 Elasticsearch 사용)
- **이벤트 소싱**: Kafka를 통한 이벤트 기반 통신

---

## 4. 프로젝트 일정

### 4.1 전체 일정 요약

| 기간 | 주차 | 핵심 목표 | 주요 산출물 |
|------|------|----------|------------|
| 12/1 ~ 12/7 | Week 1 | 인프라 구축 + 인증 + 기본 서비스 | Docker Compose, 공통 라이브러리, 인증 시스템, 프로젝트 문서들 |
| 12/8 ~ 12/15 | Week 2 | 핵심 기능 개발 + 통합 | 주문-결제-배송 플로우, 검색 기능 |
| 12/15 ~ 12/17 | Week 3 | 테스트 + 배포 + 시연 준비 | EC2 배포, 시연 시나리오 |

### 4.2 상세 일정

#### Week 1: 기반 구축 (12/1 ~ 12/7)

**목표**
- ✅ 인프라 구축 (Docker Compose)
- ✅ 공통 라이브러리 개발
- ✅ 인증 시스템 완성
- ✅ 기본 서비스 구조 완성

**주요 작업**
- Day 1-2: 프로젝트 킥오프, 프로젝트 문서작성 
- Day 3-4: 인증 서비스 개발 (70%), 기본 서비스 개발, Docker Compose 환경 구축
- Day 5-7: 인증 서비스 완성, 코드 리뷰, 개별 테스트

#### Week 2: 핵심 기능 개발 (12/8 ~ 12/14)

**목표**
- ✅ 주문 → 결제 → 배송 플로우 완성
- ✅ 검색 기능 완성
- ✅ Kafka 이벤트 통합
- ✅ 통합 테스트

**주요 작업**
- Day 8-10: 주문 서비스, 결제 서비스, 배송 서비스, 
- Day 11-13: 결제 서비스 완성, 통합 테스트, 버그 수정
- Day 14: 최종 통합 테스트, API 문서 업데이트

#### Week 3: 최종 마무리 (12/15 ~ 12/17)

**목표**
- ✅ 최종 테스트
<!-- - ✅ Kubernetes 배포 -->
- ✅ 시연 준비
- ✅ 문서 정리

**주요 작업**
- Day 15: 최종 테스트, 최종 코드 리뷰
- Day 16: 시스템 안정성 검증, 배포 준비
- Day 17: 최종 배포, 배포 검증, 시연 리허설, 발표

---

## 5. 팀 역할 및 책임

### 5.1 팀 구성

| 역할 | 담당자 | 주요 책임 | 담당 서비스                                                |
|------|--------|----------|-------------------------------------------------------|
| **🧱 아키텍트** | - | MSA 설계 + 개발 | baro-user, baro-gateway, baro-notification, front-end |
| **🧑‍💻 백엔드1** | - | 핵심 API 개발 | baro-order, baro-payment, baro-shopping, CI/CD, K8s   |
| **🧑‍💻 백엔드2** | - | 핵심 API 개발 | baro-shopping, baro-settlement, baro-ai               |
| **🧠 AI** | - | AI + 검색 | baro-shopping, baro-ai, CI/CD, K8s, front-end         |

### 5.2 역할별 상세 책임

#### 아키텍트
- 전체 시스템 아키텍처(MSA) 설계 및 기술 의사결정
- baro-user(인증/인가/회원/판매자) 개발
- baro-gateway 구축 및 라우팅/인증
- baro-notification(알림) 개발
- front-end 개발
- 코드 리뷰 및 기술 지원

#### 백엔드 개발자 1
- baro-order(주문/리뷰) 개발 (Saga 패턴 등)
- baro-payment(결제/예치금) 개발 (PG 연동)
- baro-shopping(상품/재고/장바구니) 개발
- CI/CD 파이프라인(GitHub Actions) 구축·운영
- K8s(k3s) 배포·매니페스트 관리
- Kafka 이벤트 발행/구독

#### 백엔드 개발자 2
- baro-shopping(상품/재고/장바구니) 개발
- baro-settlement(정산) 개발 및 배치/CronJob 연동
- baro-ai(검색/추천) 개발

#### AI 개발자
- baro-shopping·baro-ai 영역의 검색/추천/AI 기능 개발 (Elasticsearch 등)
- baro-ai(검색·추천) 개발 및 성능 최적화
- CI/CD, K8s 배포 지원
- front-end 개발

---

## 6. CI/CD 파이프라인

### 6.1 파이프라인 개요

바로팜 프로젝트는 **GitHub Actions**를 기반으로 한 자동화된 CI/CD 파이프라인을 구축합니다.

```
[개발자 코드 푸시]
    │
    ▼
[GitHub Repository]
    │
    ▼
[GitHub Actions]
    │
    ├─► [빌드 및 테스트]
    │   └─► Gradle 빌드
    │   └─► 단위 테스트 실행
    │
    ├─► [Docker 이미지 빌드]
    │   └─► 변경된 모듈만 빌드 (path 기반 트리거 시)
    │   └─► Multi-stage build
    │
    ├─► [ECR에 푸시]
    │   └─► {ECR}/baro-prod/{module}:tag
    │
    └─► [k3s 배포]
        └─► kubectl apply -f k8s/apps/baro-{module}/
        └─► 롤링 업데이트 / Health check
```

### 6.2 워크플로우 구성

#### 1. Build and Test
- **트리거**: Push, Pull Request (또는 path 기반)
- **작업**: 
  - JDK 21 설정
  - Gradle 빌드
  - 테스트 실행
  - 테스트 결과 업로드

#### 2. Build and Push Docker (모듈별 워크플로우)
- **트리거**: path 기반 (예: `baro-gateway/**`, `k8s/apps/baro-gateway/**`) 또는 브랜치 Push
- **작업**:
  - 해당 모듈만 빌드
  - Docker 이미지 빌드 후 ECR 푸시
  - 이미지 태그: `latest`, 버전 태그 등

#### 3. Deploy to Kubernetes (`deploy-to-k8s.yml`)
- **트리거**: `main` 브랜치 푸시, 수동 실행
- **작업**:
  - kubectl 설정
  - Kubernetes 클러스터 연결 확인
  - Deployment 이미지 업데이트
  - 롤링 업데이트 대기
  - 배포 상태 확인
  - 실패 시 자동 롤백

#### 4. Complete CI/CD Pipeline (`ci-cd-complete.yml`)
- **트리거**: `main` 브랜치 푸시
- **작업**: 전체 파이프라인 통합 실행

### 6.3 GitHub 설정

#### 필수 Secrets
- `KUBE_CONFIG`: k3s 클러스터 kubeconfig
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`: ECR 푸시용
- `MYSQL_APP_PASSWORD`, `REDIS_PASSWORD` 등 (모듈별)

#### Variables (선택사항)
- `K8S_NAMESPACE`: `default` (현재)
- 상세: [CICD_GUIDE.md](CICD_GUIDE.md), [BARO_DEPLOYMENT_GUIDE.md](BARO_DEPLOYMENT_GUIDE.md) 참고

---

## 7. 배포 전략

### 7.1 배포 환경

#### 개발 환경 (Development)
- **목적**: 로컬 개발 및 테스트
- **도구**: Docker Compose
- **데이터베이스**: 로컬 PostgreSQL, Redis, Kafka

<!-- #### 스테이징 환경 (Staging)
- **목적**: 통합 테스트 및 검증
- **도구**: Kubernetes
- **네임스페이스**: `baro-farm-staging`
- **배포**: `develop` 브랜치 자동 배포 -->

#### 운영 환경 (Production)
- **목적**: 실제 서비스 운영
- **도구**: k3s(Kubernetes), ALB + Ingress, 인프라(Redis/MySQL/Kafka/ES)는 Helm 등
- **네임스페이스**: `default` (앱), 인프라 별도 namespace (cache, db, kafka, search)
- **배포**: path 기반 또는 `main-<모듈명>` 브랜치 Push 시 해당 모듈만 배포

### 7.2 배포 전략

#### 롤링 업데이트 (Rolling Update)
- **방식**: 기존 Pod를 점진적으로 새 Pod로 교체
- **장점**: 무중단 배포 가능
- **설정**: Kubernetes Deployment의 기본 전략

#### Health Check
- **Liveness Probe**: Pod가 살아있는지 확인
- **Readiness Probe**: 트래픽을 받을 준비가 되었는지 확인
- **Startup Probe**: 애플리케이션 시작 시간 고려

#### 롤백 전략
- **자동 롤백**: Health check 실패 시 자동 롤백
- **수동 롤백**: `kubectl rollout undo` 명령어 사용
- **히스토리 관리**: Deployment revision 관리

### 7.3 Kubernetes 리소스

#### Namespace
- 앱: `default`
- 인프라: `cache`(Redis), `db`(MySQL), `kafka`, `search`(Elasticsearch) 등

#### Deployment
- 각 서비스별 Deployment 생성
- Replica: 2개 (운영 환경에서는 3개 이상 권장)
- Resource Limits: CPU 500m, Memory 512Mi

#### Service
- ClusterIP 타입 사용
- 내부 통신용

#### ConfigMap & Secret
- 환경별 설정 분리
- 민감 정보는 Secret 사용

### 7.4 배포 프로세스

1. **코드 푸시**: 해당 모듈 경로 변경 또는 `main-<모듈명>` 브랜치 Push
2. **자동 빌드**: GitHub Actions가 빌드 및 테스트 실행
3. **Docker 이미지 빌드**: 해당 모듈 이미지 빌드
4. **ECR 푸시**: 빌드된 이미지를 AWS ECR에 푸시
5. **k3s 배포**: kubectl apply로 Deployment 등 업데이트
6. **롤링 업데이트**: Pod 점진적 교체
7. **검증**: Health check 및 배포 상태 확인

---

## 8. 리스크 관리

### 8.1 기술적 리스크

| 리스크 | 영향도 | 확률 | 대응 방안 |
|--------|--------|------|----------|
| **Kafka 통합 복잡도** | 높음 | 중간 | Week 2에 Kafka 환경 확실히 구축, 간단한 이벤트부터 시작 |
<!-- | **Kubernetes 배포 실패** | 높음 | 낮음 | 로컬에서 사전 테스트, 롤백 전략 수립 | -->
| **서비스 간 통신 오류** | 중간 | 중간 | Circuit Breaker 패턴 적용, 타임아웃 설정 |
| **데이터베이스 성능** | 중간 | 낮음 | 인덱스 최적화, 쿼리 튜닝 |

### 8.2 일정 리스크

| 리스크 | 영향도 | 확률 | 대응 방안 |
|--------|--------|------|----------|
| **일정 지연** | 높음 | 중간 | 데일리스크럼, 블로커 조기 제거, 범위 조정 유연성 |
| **팀원 일정 충돌** | 높음 | 낮음 | 주간 일정 점검 |

### 8.3 비즈니스 리스크

| 리스크 | 영향도 | 확률 | 대응 방안 |
|--------|--------|------|----------|
| **요구사항 변경** | 중간 | 낮음 | 명확한 범위 정의, 변경 요청 프로세스 수립 |
| **기술 부채 누적** | 중간 | 중간 | 코드 리뷰 강화, 리팩토링 시간 확보 |

---

## 9. 성공 기준

### 9.1 기술적 성공 기준

#### 필수 (Must Have)
- ✅ 모든 핵심 서비스 정상 동작
- ✅ 인증/인가 시스템 완성
- ✅ 주문 → 결제 → 배송 플로우 완성
- ✅ Kafka 이벤트 플로우 동작
- ✅ CI/CD 파이프라인 구축
- ✅ Kubernetes 배포 성공
- ✅ 시연 가능한 수준

#### 기대 (Should Have)
- ✅ 핵심 도메인 모듈 완성 (baro-user, baro-shopping, baro-order, baro-payment 등)
- ✅ 검색 기능 동작 (baro-ai)
- ✅ 장바구니 기능 동작 (baro-shopping)
- ✅ 통합 테스트 통과
- ✅ API 문서화 완료

#### 추가 (Nice to Have)
- ✅ 모니터링 시스템 구축
- ✅ 로깅 시스템 구축
- ✅ 성능 최적화
- ✅ 보안 강화

### 9.2 비즈니스 성공 기준

- ✅ 시연 시나리오 완벽 실행
- ✅ 핵심 기능 시연 가능
- ✅ 안정적인 시스템 운영
- ✅ 확장 가능한 아키텍처

### 9.3 품질 기준

- ✅ 코드 커버리지 70% 이상
- ✅ API 응답 시간 500ms 이하
- ✅ 시스템 가용성 99% 이상
- ✅ 무중단 배포 가능

---

## 10. 문서화

### 10.1 필수 문서

- [x] README.md
- [x] [BARO_FARM_OVERVIEW.md](BARO_FARM_OVERVIEW.md) — 프로젝트 개요
- [x] [BARO_FARM_STRUCTURE.md](BARO_FARM_STRUCTURE.md) — 프로젝트/모듈 구조
- [x] [BARO_DEPLOYMENT_GUIDE.md](BARO_DEPLOYMENT_GUIDE.md) — 배포 가이드
- [x] [BARO_FARM_PLAN.md](BARO_FARM_PLAN.md) — 프로젝트 계획서

### 10.2 추가 문서

- [x] [CICD_GUIDE.md](CICD_GUIDE.md) — CI/CD 및 트러블슈팅
- [ ] 시연 스크립트
- [ ] API 사용 가이드 (Postman/curl 등)

---

## 11. 다음 단계 (최종)

### 11.1 확장 기능

- baro-notification 고도화 (알림 채널 확대)
- baro-ai 확장 (추천, 챗봇)
- 체험 예약·통계 등 추가 도메인 모듈

### 11.2 인프라 고도화

- 모니터링 시스템 구축 (Prometheus, Grafana)
- 로깅 시스템 구축 (ELK Stack)
- 분산 추적 (Zipkin/Jaeger)

### 11.3 성능 최적화

- 데이터베이스 최적화
- 캐싱 전략 개선
- CDN 도입
- 로드 밸런싱 고도화

---

*최종 업데이트: 2026년 3월 (현재 도메인 모듈 구조 반영)*

