# Baro-Farm 배포 아키텍처 변경 이력

과거 문서(V1, V2)와 **현재 저장소 기준 배포 구조(v3)** 의 차이 요약입니다.  
실제 설정·경로·워크플로는 [BARO_DEPLOYMENT_GUIDE.md](BARO_DEPLOYMENT_GUIDE.md)를 따릅니다.

---

## 버전별 요약

| 구분 | V1 (과거) | V2 (과거) | **V3 (현재 저장소 기준)** |
|------|-----------|-----------|---------------------------|
| **외부 진입점** | hostNetwork + 노드 IP:8080 | 동일 | **ALB + Ingress** (alb, internet-facing, target-type: instance) |
| **Gateway** | ClusterIP | ClusterIP | **NodePort 30399**, Service port 80 → 8080 |
| **로드밸런서** | 없음 / k3s ServiceLB | 동일 | **AWS ALB + AWS LB Controller** |
| **데이터 인프라** | Data Plane EC2 + Docker Compose | 동일 | **k8s** (Redis/MySQL/ES: Helm, Kafka: Strimzi), ns: cache, db, kafka, search |
| **이미지** | GHCR | GHCR | **AWS ECR** (299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/*) |
| **배포 트리거** | main-\* 브랜치 | main-\* 브랜치 | **앱: path 기반** (브랜치 무관) / **인프라: main + path** 또는 workflow_dispatch |
| **서비스 디스커버리** | Eureka | Eureka | **Kubernetes Service DNS** (예: baro-user:8080) |

---

## 모듈 구조 변화

- **V1**: baro-auth, baro-buyer, baro-seller, baro-support, baro-settlement 등 (역할/유형 혼합)
- **V2**: baro-user, baro-shopping, baro-order, baro-payment, baro-notification, baro-batch, baro-ai (도메인 기준)
- **V3**: 위와 동일한 도메인 구조, **baro-settlement** 사용 (baro-batch 아님). 모든 모듈에 `k8s/apps/baro-<module>/` 및 `baro-<module>-deploy.yml` 존재. 앱 네임스페이스 `default`.

---

## 정리

- **V1**: 초기 MSA(Data/Application Plane, Eureka, hostNetwork). 구 모듈명.
- **V2**: 도메인 기준 모듈 재구성. 인프라 구성은 V1과 동일.
- **V3**: ALB·Ingress, ECR, path 기반 앱 배포, 인프라 k8s(Helm/Strimzi). **현재 저장소가 반영하는 운영 기준**은 V3입니다.
