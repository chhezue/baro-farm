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
| **이미지 레지스트리** | AWS ECR `299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/*` |
| **외부 진입** | AWS ALB → Ingress(alb, internet-facing) → baro-gateway Service(NodePort 30399) |
| **서비스 발견** | Eureka 미사용, Kubernetes Service DNS 직접 사용 (예: `http://baro-user:8080`) |

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

| 인프라 | Namespace | 배포 방식 | 서비스 호스트 예시 |
|--------|-----------|-----------|-------------------|
| **Redis** | `cache` | Helm (Bitnami, release: my-redis) | `redis-master.cache.svc.cluster.local:6379` |
| **MySQL** | `db` | Helm (Bitnami, release: my-mysql) | `my-mysql.db.svc.cluster.local:3306` |
| **Kafka** | `kafka` | Strimzi/Helm (release: my-kafka) | `my-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092` |
| **Elasticsearch** | `search` | Helm (release: my-elasticsearch) | `my-elasticsearch.search.svc.cluster.local:9200` |

- 인프라 워크플로: **main** 브랜치 Push + 해당 path 변경 시, 또는 `workflow_dispatch` 수동 실행.
- 앱에서 사용: **ConfigMap `infra-endpoints-config`** (`k8s/apps/common/infra-configmap.yml`에 정의)를 envFrom으로 참조.

---

## 5. 모듈 구성

| 모듈 | 용도 | K8s/워크플로 | 비고 |
|------|------|--------------|------|
| **baro-gateway** | API Gateway, 라우팅/인증 | ✅ 매니페스트·워크플로 있음 | 실제 배포 대상 |
| **baro-sample** | 샘플/테스트 | ✅ 매니페스트·워크플로 있음 | 실제 배포 대상 |
| **baro-user** | 인증/회원/판매자 | ✅ 매니페스트·워크플로 있음 | path 기반 배포 가능 |
| **baro-shopping** | 상품/재고/장바구니 | ✅ 매니페스트·워크플로 있음 | path 기반 배포 가능 |
| **baro-order** | 주문/리뷰 | ✅ 매니페스트·워크플로 있음 | path 기반 배포 가능 |
| **baro-payment** | 결제/입금 | ✅ 매니페스트·워크플로 있음 | path 기반 배포 가능 |
| **baro-notification** | 알림 | ✅ 매니페스트·워크플로 있음 | path 기반 배포 가능 |
| **baro-settlement** | 정산 | ✅ 매니페스트·CronJob·워크플로 있음 | prod Gateway 라우팅은 추가 예정 |
| **baro-ai** | AI/검색/추천 | ✅ 매니페스트·워크플로 있음 | path 기반 배포 가능 |

클러스터에 실제로 올라가 있는지는 환경별로 다를 수 있으며, 모든 모듈은 `k8s/apps/baro-<module>/` 및 `.github/workflows/baro-<module>-deploy.yml` 로 배포 가능합니다.

---

## 6. Gateway 라우팅 (application-prod.yml 기준)

| 경로 prefix | 백엔드 Service (호스트:포트) |
|-------------|-----------------------------|
| `/sample-service/**` | baro-sample:8080 |
| `/user-service/**` | baro-user:8080 |
| `/order-service/**` | baro-order:8080 |
| `/payment-service/**` | baro-payment:8080 |
| `/shopping-service/**` | baro-shopping:8080 |
| `/notification-service/**` | baro-notification:8080 |
| `/ai-service/**` | baro-ai:8080 |
| `/settlement-service/**` | prod 라우팅 미등록 (application-local.yml 에만 존재, 추가 예정) |

---

## 7. 배포 리소스 요약 (실제 설정 기준)

### baro-gateway

- **Deployment**: replicas 1, image `299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/baro-gateway:latest`
- **Service**: type NodePort, port 80 → targetPort 8080, **nodePort 30399**
- **Ingress**: `baro-gateway-ingress`, ingress.class alb, scheme internet-facing, target-type instance, listen HTTP:80, healthcheck-path `/actuator/health`
- **ConfigMap**: `baro-gateway-config`  
- **Secrets**: `gateway-secrets`(JWT), `redis-credentials`
- **Workflow**: `baro-gateway-deploy.yml` — 트리거: `baro-gateway/**`, `docker/baro-gateway/**`, `k8s/apps/baro-gateway/**`, `k8s/apps/common/**` 변경 시 (브랜치 제한 없음)

### baro-sample

- **Deployment**: replicas 1, containerPort 8090, envFrom: `baro-sample-config`, `infra-endpoints-config`, Secrets: `mysql-credentials`, `redis-credentials`
- **Service**: type NodePort, port 8080 → targetPort 8090, **nodePort 30080**
- **Workflow**: `baro-sample-deploy.yml` — 트리거: `baro-sample/**`, `docker/baro-sample/**`, `k8s/apps/baro-sample/**` 변경 시

---

## 8. 모듈 배포 공통 패턴 (이미 적용된 구조)

1. **디렉터리**: `k8s/apps/baro-<module>/` — configmap.yml, deployment.yml, service.yml (필요 시 cronjob.yml)
2. **Service**: Gateway에서 호출하는 백엔드는 **ClusterIP** 또는 NodePort(디버깅용). Gateway만 NodePort(30399)로 외부 노출.
3. **ConfigMap**: 모듈별 config + `infra-endpoints-config` envFrom (`k8s/apps/common/infra-configmap.yml`)
4. **Secrets**: `mysql-credentials`, `redis-credentials` 등 워크플로에서 생성 후 배포 시 적용
5. **이미지**: ECR `299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/baro-<module>:latest` (태그는 워크플로에서 sha 등 사용)
6. **Workflow**: `.github/workflows/baro-<module>-deploy.yml` — **path 기반** 트리거 (`baro-<module>/**`, `docker/baro-<module>/**`, `k8s/apps/baro-<module>/**`, `k8s/apps/common/**` 등), 네임스페이스 `default`

권장 배포 순서: **baro-user → baro-shopping → baro-order → baro-payment → baro-notification → baro-settlement → baro-ai**

---

## 9. 디렉터리 구조 (현재 저장소 기준)

```
k8s/
├── apps/
│   ├── common/
│   │   └── infra-configmap.yml    # ConfigMap 이름: infra-endpoints-config
│   ├── baro-gateway/              # configmap, deployment, service, ingress.yml.yaml
│   ├── baro-sample/
│   ├── baro-user/
│   ├── baro-shopping/
│   ├── baro-order/
│   ├── baro-payment/
│   ├── baro-notification/
│   ├── baro-settlement/           # configmap, deployment, service, cronjob.yml
│   └── baro-ai/
└── infra/
    ├── storageclass-gp3-ebs.yml
    ├── mysql/                     # Helm Chart + values-mysql.yml
    ├── redis/                     # Helm Chart + values-redis.yml
    ├── kafka/                     # strimzi-kafka.yml
    └── elasticsearch/             # Helm Chart + values-elasticsearch.yml

.github/workflows/
├── baro-gateway-deploy.yml
├── baro-sample-deploy.yml
├── baro-user-deploy.yml
├── baro-shopping-deploy.yml
├── baro-order-deploy.yml
├── baro-payment-deploy.yml
├── baro-notification-deploy.yml
├── baro-settlement-deploy.yml
├── baro-ai-deploy.yml
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
