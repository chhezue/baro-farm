# Baro-Farm 배포 아키텍처 변경 이력

과거 문서(V1, V2)와 현재(v3) 구조의 **차이를 한눈에 보기 위한** 요약입니다.  
**현재 배포 방식**은 [BARO_DEPLOYMENT_GUIDE.md](BARO_DEPLOYMENT_GUIDE.md)를 참고하세요.

---

## 버전별 요약

| 구분 | V1 (과거) | V2 (과거) | **V3 (현재)** |
|------|-----------|-----------|----------------|
| **외부 진입점** | hostNetwork + 노드 IP:8080 | 동일 | **ALB + Ingress** |
| **Gateway** | ClusterIP | ClusterIP | **NodePort (30399)** |
| **로드밸런서** | 없음 / k3s ServiceLB | 동일 | **AWS ALB + LB Controller** |
| **데이터 인프라** | Data Plane EC2 + Docker Compose | 동일 | **k8s Helm** (cache, db, kafka, search ns) |
| **이미지** | GHCR | GHCR | **AWS ECR** |
| **배포 트리거** | main-\* 브랜치 | main-\* 브랜치 | **path 기반** (변경된 모듈만 배포) |
| **서비스 디스커버리** | Eureka | Eureka | **Service DNS 직접 사용** |

---

## 모듈 구조 변화

- **V1**: baro-auth, baro-buyer, baro-seller, baro-support, baro-settlement 등 (역할/유형 혼합)
- **V2**: baro-user, baro-shopping, baro-order, baro-payment, baro-notification, baro-batch, baro-ai (도메인 기준)
- **V3**: V2와 동일한 도메인 구조, **baro-settlement** 사용 (baro-batch 대신), baro-gateway / baro-sample 실제 배포

---

## 정리

- **V1**: 초기 MSA 설계(Data/Application Plane 분리, Eureka, hostNetwork 등). 모듈명은 구 구조.
- **V2**: 도메인 기반 모듈로 재구성( user / shopping / order / payment / notification / batch / ai ). 인프라 구성은 V1과 동일.
- **V3**: ALB·Ingress 도입, ECR·path 기반 배포, 인프라를 k8s Helm으로 이전. **현재 운영 기준**은 V3입니다.
