# CI/CD 가이드

Baro Farm 프로젝트의 GitHub Actions 기반 CI/CD 파이프라인 구축 가이드입니다.

## 📋 목차

- [CI/CD 개요](#cicd-개요)
- [파이프라인 구조](#파이프라인-구조)
- [인프라 구성](#인프라-구성)
- [사전 준비사항](#사전-준비사항)
- [GitHub Secrets 설정](#github-secrets-설정)
- [AWS EC2 설정](#aws-ec2-설정)
- [배포 프로세스](#배포-프로세스)
- [트러블슈팅](#트러블슈팅)

---

## CI/CD 개요

### 사용 기술

- **CI/CD**: GitHub Actions
- **컨테이너**: Docker
- **레지스트리**: AWS ECR (baro-prod/*)
- **배포 환경**: AWS EC2 (k3s 클러스터)
- **오케스트레이션**: Kubernetes (k3s), 인프라 일부 Helm (Redis, MySQL, Kafka, Elasticsearch)
- **배포 도구**: kubectl apply (매니페스트 직접 적용), Kustomize(선택)

### 자동화 범위

```
Code Push (path 기반) → CI (빌드/테스트) → Docker Image Build → 
ECR Push → k3s 클러스터 배포 → Health Check
```

> **참고:** 현재 배포 구조 상세는 [BARO_DEPLOYMENT_GUIDE.md](BARO_DEPLOYMENT_GUIDE.md)를 참고하세요.

---

## 파이프라인 구조

### 1. CI (Continuous Integration)

**트리거:**
- `main`, `main-*`, `dev-*` 브랜치에 Push
- Pull Request 생성

**작업:**
1. ✅ 코드 포맷 검사 (Spotless)
2. ✅ 코드 스타일 검사 (Checkstyle)
3. ✅ Gradle 빌드
4. ✅ 단위 테스트 실행
5. ✅ 빌드 아티팩트 저장

### 2. Docker Build

**트리거:**
- **path 기반**: 해당 모듈/경로 변경 시 (예: `baro-gateway/**`, `k8s/apps/baro-gateway/**`)
- 또는 `main` / `main-*` 브랜치 Push (워크플로우별 설정)

**작업:**
1. ✅ Docker 이미지 빌드
2. ✅ AWS ECR에 Push
3. ✅ 이미지 태그: `latest`, 버전 태그 등

**빌드 대상 서비스 (현재/예정):**
- baro-gateway, baro-sample
- baro-user, baro-shopping, baro-order, baro-payment
- baro-notification, baro-settlement, baro-ai

### 3. CD (Continuous Deployment)

**트리거:**
- path 기반 트리거로 **변경된 모듈만** 배포 (권장)
- 또는 브랜치 Push 시 해당 모듈 워크플로 실행

**작업:**
1. ✅ Docker 이미지 빌드 및 ECR Push
2. ✅ k3s 클러스터에 배포 (kubectl apply -f)
3. ✅ ConfigMap/Secret 반영, Pod 상태 확인
4. ✅ Health Check

---

## 인프라 구성

이 프로젝트는 **k3s 클러스터** 환경에서 운영됩니다.

### 클러스터 구성 (현재)

- **k3s (EC2 기반)**
  - **앱 네임스페이스**: `default`
  - **외부 진입**: AWS ALB → Ingress → baro-gateway (NodePort 30399)
  - **서비스 디스커버리**: Eureka 미사용, Kubernetes Service DNS 직접 사용

- **인프라 (별도 namespace, Helm 등으로 배포)**
  - Redis → `cache`, MySQL → `db`, Kafka → `kafka`, Elasticsearch → `search`

- **애플리케이션 (k8s Pod)**
  - baro-gateway, baro-sample (배포됨)
  - baro-user, baro-shopping, baro-order, baro-payment, baro-notification, baro-settlement, baro-ai (배포 시 동일 패턴)

### 배포 방식

- **인프라**: k8s 내 Helm 또는 별도 배포 (Redis, MySQL, Kafka, Elasticsearch)
- **애플리케이션**: k3s에 매니페스트 적용 (kubectl apply -f)
- **CI/CD**: GitHub Actions에서 ECR 푸시 후 k3s 클러스터에 배포

### 디렉토리 구조 (현재)

```
k8s/
├── apps/
│   ├── common/             # 공통 ConfigMap 등
│   ├── baro-gateway/
│   └── baro-sample/        # baro-user, baro-order 등 동일 패턴으로 추가
└── infra/                  # Helm 배포용 (mysql, redis, kafka, elasticsearch)
```

---

## 사전 준비사항

### 1. AWS ECR (Elastic Container Registry) 설정

**이미지 저장소는 AWS ECR에 생성합니다.**

```bash
# 이미지 형식:
{ECR_REGISTRY}/baro-prod/{service-name}:tag

# 예시:
299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/baro-gateway:latest
299369991605.dkr.ecr.ap-northeast-2.amazonaws.com/baro-prod/baro-user:latest
...
```

**사전 준비:**
- ECR에 `baro-prod/baro-gateway`, `baro-prod/baro-sample` 등 저장소 생성
- GitHub Secrets에 AWS 자격증명 및 `KUBE_CONFIG`(또는 k3s 접근용 설정) 등록

### 2. AWS EC2 인스턴스

**최소 사양:**
- **Master Node**: t3.medium (2 vCPU, 4GB RAM)
- **Worker Node**: t3.medium (2 vCPU, 4GB RAM)
- OS: Ubuntu 22.04 LTS
- Storage: 30GB 이상
- Security Group: 8080-8092, 8761, 8888 포트 오픈

### 3. k3s 클러스터 설치

**Master Node:**
```bash
# k3s 설치
curl -sfL https://get.k3s.io | sh -

# kubeconfig 확인
sudo cat /etc/rancher/k3s/k3s.yaml

# kubectl 설정
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
```

**Worker Node:**
```bash
# Master Node에서 토큰 확인
sudo cat /var/lib/rancher/k3s/server/node-token

# Worker Node에서 k3s 설치 (Master Node IP와 토큰 사용)
curl -sfL https://get.k3s.io | K3S_URL=https://<MASTER_IP>:6443 K3S_TOKEN=<TOKEN> sh -
```

### 4. GitHub Actions Runner 설치

각 서버(Master Node, Worker Node)에 self-hosted runner를 설치해야 합니다.

**설치 위치:**
- Master Node: `/home/ubuntu/actions-runner`
- Worker Node: `/home/ubuntu/actions-runner`

**설치 방법:**
```bash
# GitHub → Settings → Actions → Runners → New self-hosted runner
# 지시에 따라 다운로드 및 설치

cd ~/actions-runner
./config.sh --url https://github.com/OWNER/REPO --token <TOKEN>
./run.sh
```

**자세한 가이드:** [SELF_HOSTED_RUNNER_GUIDE.md](./SELF_HOSTED_RUNNER_GUIDE.md) 참조

### 5. Docker 및 Docker Compose 설치

```bash
# Docker 설치
sudo apt update
sudo apt install -y docker.io docker-compose
sudo systemctl start docker
sudo systemctl enable docker

# 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER
newgrp docker

# Docker 버전 확인
docker --version
docker-compose --version
```

---

## GitHub Secrets 설정

GitHub 레포지토리 → Settings → Secrets and variables → Actions → New repository secret

### 필수 Secrets

| Secret 이름 | 설명 | 필요 여부 |
|-------------|------|----------|
| `AWS_ACCESS_KEY_ID` | AWS Access Key (ECR, 기타 리소스) | ✅ 필수 |
| `AWS_SECRET_ACCESS_KEY` | AWS Secret Key | ✅ 필수 |
| `KUBE_CONFIG` | k3s 클러스터 접근용 kubeconfig 내용 | ✅ 필수 (또는 Runner 내 설정) |
| `MYSQL_APP_PASSWORD` | MySQL 앱용 비밀번호 (모듈별 DB) | ✅ DB 사용 모듈 |
| `REDIS_PASSWORD` | Redis 비밀번호 | ✅ Redis 사용 모듈 |
| `DATA_EC2_IP` | Data EC2 Private IP (인프라 접속용, legacy 구성 시) | ⚠️ 구성에 따라 |
| `EC2_HOST` | EC2 Public IP (SSH/수동 디버깅용) | ⚠️ 선택 |
| `EC2_USERNAME` | EC2 SSH 사용자명 (예: `ubuntu`) | ⚠️ 선택 |
| `EC2_SSH_KEY` | EC2 SSH Private Key (.pem 내용) | ⚠️ 선택 |
| `TOSS_SECRET_KEY` | Toss Payments Secret Key (결제 모듈) | ✅ 결제 모듈 사용 시 |

**참고:** ECR 푸시는 AWS 자격증명으로 수행하며, k3s 배포는 `KUBE_CONFIG` 또는 Runner가 이미 접근 가능한 클러스터를 사용합니다.

### GitHub Variables (환경 변수)

GitHub Repository → Settings → Secrets and variables → Actions → Repository variables

| Variable | 예시 값 | 설명 |
|----------|---------|------|
| `DEPLOY_KAFKA` | `true` / `false` | k3s 환경에서 Kafka를 배포할지 여부 (Docker Compose 기반 Kafka 사용 제어) |
| `DEPLOY_ELASTICSEARCH` | `true` / `false` | k3s 환경에서 Elasticsearch를 배포할지 여부 (Docker Compose 기반 ES 사용 제어) |

---

## AWS EC2 설정

### 1. Security Group 설정

**Inbound Rules (참고):**

| Type | Protocol | Port Range | Source | Description |
|------|----------|-----------|--------|-------------|
| SSH | TCP | 22 | My IP | SSH 접속 |
| HTTP | TCP | 80 | 0.0.0.0/0 | ALB → Gateway (외부 진입) |
| Custom TCP | TCP | 8080 | (ALB/내부) | Gateway |
| Custom TCP | TCP | 8082 | 내부 | baro-shopping |
| Custom TCP | TCP | 8083 | 내부 | baro-order |
| Custom TCP | TCP | 8087 | 내부 | baro-user |
| Custom TCP | TCP | 8088 | 내부 | baro-payment |
| Custom TCP | TCP | 8090 | 내부 | baro-sample |
| Custom TCP | TCP | 8091 | 내부 | baro-notification |
| Custom TCP | TCP | 8092 | 내부 | baro-ai |
| Custom TCP | TCP | 8093 | 내부 | baro-settlement |

> 실제 외부 진입은 ALB(:80) → Gateway이므로, 서비스 포트는 보안 그룹에서 필요 시 제한합니다.

### 2. EC2 초기 설정

**Master Node:**
```bash
# EC2 접속
ssh -i your-key.pem ubuntu@master-ec2-ip

# 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# Docker 설치
sudo apt install -y docker.io docker-compose
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ubuntu
newgrp docker

# k3s 설치
curl -sfL https://get.k3s.io | sh -

# kubectl 설정
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config

# GitHub Actions Runner 설치
cd ~
mkdir actions-runner && cd actions-runner
# GitHub에서 제공하는 다운로드 URL 및 토큰 사용
```

**Worker Node:**
```bash
# EC2 접속
ssh -i your-key.pem ubuntu@worker-ec2-ip

# 시스템 업데이트 및 Docker 설치 (동일)
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ubuntu
newgrp docker

# k3s Worker 설치 (Master Node 토큰 필요)
sudo cat /var/lib/rancher/k3s/server/node-token  # Master Node에서 실행
curl -sfL https://get.k3s.io | K3S_URL=https://<MASTER_IP>:6443 K3S_TOKEN=<TOKEN> sh -

# GitHub Actions Runner 설치 (동일)
```

---

## 배포 프로세스

### 자동 배포 (GitHub Actions)

```bash
# 1. 코드 변경 후 커밋
git add .
git commit -m "[Feat] #123 - 새로운 기능 추가"

# 2. 해당 모듈 경로가 포함된 브랜치에 Push
#    (path 기반 트리거 시: baro-gateway/**, k8s/apps/baro-gateway/** 등 변경)

# 3. GitHub Actions 자동 실행
# - 코드 품질 검사 (Spotless, Checkstyle)
# - 빌드 및 테스트
# - Docker 이미지 빌드 → ECR Push
# - k3s 클러스터에 배포 (kubectl apply -f)

# 4. 배포 확인
# - ALB URL 또는 http://<노드>:8080 (Gateway)
# - 배포 가이드: docs/BARO_DEPLOYMENT_GUIDE.md
```

### 수동 배포 (k3s 클러스터에서 직접)

```bash
# kubeconfig가 설정된 환경에서
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin {ECR_REGISTRY}

# 이미지 Pull (필요 시)
docker pull {ECR_REGISTRY}/baro-prod/baro-gateway:latest

# k8s 배포
kubectl apply -f k8s/apps/common/
kubectl apply -f k8s/apps/baro-gateway/

# 배포 상태 확인 (네임스페이스: default)
kubectl get pods -n default -l app=baro-gateway

# Pod 로그 확인
kubectl logs -n default -l app=baro-gateway --tail=100 -f
```

---

## 배포 확인

### 1. Pod 상태 확인

```bash
# default 네임스페이스 기준
kubectl get pods -n default

# 특정 서비스 Pod 확인
kubectl get pods -n default -l app=baro-gateway
kubectl get pods -n default -l app=baro-sample
```

### 2. Health Check

```bash
# Gateway (ALB 또는 노드:8080 경유)
curl http://<ALB-URL 또는 노드>:8080/actuator/health

# Sample
curl http://<노드>:8090/actuator/health

# 기타 서비스는 Gateway 경유로만 외부 노출 (내부 포트는 배포 가이드 참고)
```

### 3. 로그 확인

```bash
kubectl logs -n default -l app=baro-gateway --tail=100 -f
kubectl logs -n default <pod-name> -f
```

### 4. 배포 상태 상세 확인

```bash
kubectl get deployments -n default
kubectl get services -n default
kubectl get events -n default --sort-by='.lastTimestamp'
```

---

## 트러블슈팅

### 1. GitHub Actions 빌드 실패

**문제:** Spotless 또는 Checkstyle 실패

```bash
# 로컬에서 검사
./gradlew spotlessCheck
./gradlew checkstyleMain

# 자동 수정
./gradlew spotlessApply

# 재커밋
git add .
git commit -m "[Fix] 코드 포맷 수정"
git push
```

### 2. Docker 이미지 빌드 실패

**문제:** Dockerfile을 찾을 수 없음

```bash
# Dockerfile 경로 확인
ls -la docker/*/Dockerfile

# 필요시 Dockerfile 생성
# 프로젝트 루트의 docker/ 폴더 확인
```

### 3. EC2 배포 실패

**문제:** SSH 연결 실패

```bash
# EC2_SSH_KEY 형식 확인
-----BEGIN RSA PRIVATE KEY-----
...전체 내용...
-----END RSA PRIVATE KEY-----

# EC2 Security Group에서 SSH (22번 포트) 허용 확인
# EC2 인스턴스가 실행 중인지 확인
```

### 4. Pod 시작 실패

**문제:** Pod가 계속 재시작하거나 CrashLoopBackOff 상태

```bash
# Pod 로그 확인
kubectl logs -n default -l app=baro-gateway --tail=100

# Pod 상태 상세 확인
kubectl describe pod -n default -l app=baro-gateway

# 일반적인 원인:
# - MySQL/Redis/Kafka 등 인프라 연결 실패 (ConfigMap infra-endpoints-config 확인)
# - 환경 변수/Secret 누락
# - 메모리 부족

# 배포 순서 (Gateway 먼저, 이후 비즈니스 모듈)
kubectl apply -f k8s/apps/common/
kubectl apply -f k8s/apps/baro-gateway/
# 이후 baro-user, baro-shopping 등 모듈별 적용
```

### 5. 메모리 부족

**문제:** EC2 메모리 부족으로 Pod 종료

```bash
# 메모리 사용량 확인
free -h
kubectl top nodes
kubectl top pods -n default

# 해결방법:
# 1. EC2 인스턴스 타입 업그레이드 (t3.medium → t3.large)
# 2. 일부 서비스만 배포
# 3. JVM 메모리 설정 조정 (deployment.yaml에서 JAVA_OPTS)
# 4. Pod 리소스 제한 조정
```

### 6. 이미지 Pull 실패

**문제:** ECR에서 이미지를 가져올 수 없음

```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin {ECR_REGISTRY}

# k3s에서 ECR 사용 시: imagePullSecrets (ecr-registry) 설정 확인
# GitHub Actions에서 ECR 푸시 후 배포 시에는 보통 Runner/클러스터가 이미 인증된 상태
```

### 7. baro-settlement (정산) 배포

**참고:** baro-settlement는 Deployment + CronJob(정산 배치)으로 배포합니다. DaemonSet이 아닙니다.

```bash
kubectl apply -f k8s/apps/baro-settlement/
kubectl get cronjobs -n default
```

---

## 고급 설정

### 롤링 업데이트

k3s는 기본적으로 롤링 업데이트를 지원합니다:

```yaml
# deployment.yml
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
```

### 이미지 태그 업데이트 및 롤백

```bash
# deployment.yml에서 image 태그 변경 또는
kubectl set image deployment/baro-gateway baro-gateway={ECR}/baro-prod/baro-gateway:이전태그 -n default

# 롤백
kubectl rollout undo deployment/baro-gateway -n default
```

### 버전 관리

ECR 이미지 태그 예시:
```
{ECR_REGISTRY}/baro-prod/baro-gateway:
├── latest
├── main-abc123d
└── 0.1.0
```

### 리소스 모니터링

```bash
# 노드 리소스 확인
kubectl top nodes

# Pod 리소스 확인
kubectl top pods -n default

# 특정 Pod 상세 정보
kubectl describe pod <pod-name> -n default
```

---

## 참고 자료

- [BARO_DEPLOYMENT_GUIDE.md](BARO_DEPLOYMENT_GUIDE.md) — 현재 배포 구조 (팀/접근자용)
- [GitHub Actions 공식 문서](https://docs.github.com/actions)
- [k3s 공식 문서](https://k3s.io/)
- [Kubernetes 공식 문서](https://kubernetes.io/docs/)
- [Docker 공식 문서](https://docs.docker.com/)
- [AWS ECR 사용자 가이드](https://docs.aws.amazon.com/ecr/)
- [AWS EC2 사용자 가이드](https://docs.aws.amazon.com/ec2/)

