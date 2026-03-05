# Baro-Farm 배포 가이드

팀원 및 서비스 접근자를 위한 **현재 배포 구조** 요약입니다.  
아키텍처 변경 이력은 [BARO_DEPLOYMENT_HISTORY.md](BARO_DEPLOYMENT_HISTORY.md)를 참고하세요.

---

## 1. 읽기 대상

- **운영/배포 담당**: 현재 인프라·배포 방식 파악
- **개발자**: 서비스 구성·라우팅·모듈 추가 시 참고
- **외부 연동 담당**: 진입점(ALB)·API 경로 이해

---

## 2. 전체 구조 요약

| 구분 | 내용 |
|------|------|
| **클러스터** | k3s (EC2 기반) |
| **앱 네임스페이스** | `default` |
| **이미지 레지스트리** | AWS ECR (`baro-prod/*`) |
| **외부 진입** | AWS ALB → Ingress → baro-gateway (NodePort 30399) |
| **서비스 발견** | Eureka 미사용, Kubernetes Service DNS 직접 사용 |

---

## 3. 트래픽 흐름

```
사용자 → ALB(:80) → Ingress → baro-gateway Service(NodePort 30399) → baro-gateway Pod
                                                                           ↓
                                    baro-user, baro-shopping, baro-order, ... (ClusterIP)
                                                                           ↓
                                    Redis(cache), MySQL(db), Kafka(kafka), Elasticsearch(search)
```

- ALB는 **instance target**으로 각 노드의 NodePort(30399)에 트래픽을 보냅니다.
- Gateway만 외부 노출되고, 나머지 서비스는 Gateway 경유로만 접근합니다.

---

## 4. 인프라 구성

| 인프라 | Namespace | 배포 방식 | 비고 |
|--------|-----------|-----------|------|
| **Redis** | `cache` | Helm (my-redis) | 캐시/세션 |
| **MySQL** | `db` | Helm (my-mysql) | 메인 DB |
| **Kafka** | `kafka` | Helm (Strimzi 등) | 메시지 큐 |
| **Elasticsearch** | `search` | Helm (my-elasticsearch) | 검색 |

애플리케이션은 **ConfigMap `infra-endpoints-config`** 로 위 인프라 엔드포인트를 참조합니다.

---

## 5. 모듈 구성

| 모듈 | 용도 | 배포 상태 |
|------|------|-----------|
| **baro-gateway** | API Gateway, 라우팅/인증 | ✅ 배포됨 |
| **baro-sample** | 샘플/테스트 | ✅ 배포됨 |
| **baro-user** | 인증/회원/판매자 | ❌ 미배포 |
| **baro-shopping** | 상품/재고/장바구니 | ❌ 미배포 |
| **baro-order** | 주문/리뷰 | ❌ 미배포 |
| **baro-payment** | 결제/입금 | ❌ 미배포 |
| **baro-notification** | 알림 | ❌ 미배포 |
| **baro-settlement** | 정산 | ❌ 미배포 |
| **baro-ai** | AI/검색/추천 | ❌ 미배포 |

---

## 6. Gateway 라우팅 (application-prod 기준)

| 경로 prefix | 백엔드 서비스 |
|-------------|----------------|
| `/user-service/**` | baro-user |
| `/shopping-service/**` | baro-shopping |
| `/order-service/**` | baro-order |
| `/payment-service/**` | baro-payment |
| `/notification-service/**` | baro-notification |
| `/settlement-service/**` | baro-settlement |
| `/ai-service/**` | baro-ai |
| `/sample-service/**` | baro-sample |

---

## 7. 배포된 리소스 요약

### baro-gateway

- **Deployment**: replicas 1, 이미지 `baro-prod/baro-gateway`
- **Service**: NodePort, port 80 → 8080, nodePort 30399
- **Ingress**: alb, internet-facing, target-type: instance
- **Workflow**: `baro-gateway-deploy.yml` (path 기반 트리거)

### baro-sample

- **Deployment**: replicas 1, port 8090, nodePort 30080
- **Workflow**: `baro-sample-deploy.yml`

---

## 8. 새 모듈 배포 시 공통 패턴

1. **디렉터리**: `k8s/apps/baro-<module>/` (configmap, deployment, service)
2. **Service**: Gateway에서만 호출하므로 **ClusterIP** 사용 (외부 노출은 Gateway 경유만)
3. **ConfigMap**: `SPRING_PROFILES_ACTIVE=prod`, `SERVER_PORT`, `infra-endpoints-config` envFrom
4. **Secrets**: DB/Redis 등 사용 시 `mysql-credentials`, `redis-credentials` 등
5. **이미지**: ECR `baro-prod/baro-<module>`, Dockerfile `docker/baro-<module>/Dockerfile`
6. **Workflow**: path 기반 트리거로 `baro-<module>/**`, `k8s/apps/baro-<module>/**` 등 변경 시 배포

권장 배포 순서: **baro-user → baro-shopping → baro-order → baro-payment → baro-notification → baro-settlement → baro-ai**

---

## 9. 디렉터리 구조

```
k8s/
├── apps/
│   ├── common/          # 공통 ConfigMap 등
│   ├── baro-gateway/
│   └── baro-sample/
└── infra/               # Helm 배포용 (mysql, redis, kafka, elasticsearch)

docker/
├── baro-gateway/Dockerfile
└── baro-sample/Dockerfile

.github/workflows/
├── baro-gateway-deploy.yml
├── baro-sample-deploy.yml
├── redis-deploy.yml
├── mysql-deploy.yml
├── kafka-deploy.yml
└── elasticsearch-deploy.yml
```

---

## 10. 참고 문서

- [BARO_DEPLOYMENT_HISTORY.md](BARO_DEPLOYMENT_HISTORY.md) — V1/V2/V3 아키텍처 변경 이력
- [CICD_GUIDE.md](CICD_GUIDE.md) — CI/CD 상세 설정
- [BARO_FARM_STRUCTURE.md](BARO_FARM_STRUCTURE.md) — 프로젝트/모듈 구조
