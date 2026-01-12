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
- **레지스트리**: GitHub Container Registry (GHCR)
- **배포 환경**: AWS EC2 (k3s 클러스터)
- **오케스트레이션**: Kubernetes (k3s) + Docker Compose
- **배포 도구**: Kustomize

### 자동화 범위

```
Code Push → CI (빌드/테스트) → Docker Image Build → 
GHCR Push → k3s 클러스터 배포 → Health Check
```

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
- `main` 또는 `main-*` 브랜치에 Push

**작업:**
1. ✅ Docker 이미지 빌드
2. ✅ GitHub Container Registry에 Push
3. ✅ 이미지 태그: `latest`, `{branch}-{sha}`

**빌드 대상 서비스:**
- baro-auth
- baro-buyer
- baro-seller
- baro-order
- baro-payment
- baro-support
- baro-settlement
- baro-ai
- eureka
- gateway
- config

### 3. CD (Continuous Deployment)

**트리거:**
- `main-*` 브랜치에 Push (모듈별 프로덕션 배포)

**작업:**
1. ✅ Self-hosted Runner에서 실행
2. ✅ Docker 이미지 빌드 및 GHCR Push
3. ✅ k3s 클러스터에 배포 (kubectl apply -k)
4. ✅ Kustomize를 통한 이미지 태그 업데이트
5. ✅ Pod 상태 확인 및 Health Check

---

## 인프라 구성

이 프로젝트는 **k3s 클러스터** 환경에서 운영됩니다.

### 클러스터 구성

- **k3s Master Node (t3.medium 4GB)**
  - GitHub Actions Runner 설치
  - MySQL, Redis, Elasticsearch (Docker Compose)
  - Eureka, Config, Gateway (k3s Pod)
  - Kubernetes API Server, etcd 등 k3s 제어 플레인

- **k3s Worker Node (t3.medium 4GB)**
  - GitHub Actions Runner 설치
  - Kafka (Docker Compose)
  - 비즈니스 서비스 모듈 (k3s Pod)
    - baro-auth, baro-buyer, baro-seller, baro-order, baro-payment, baro-support, baro-settlement (DaemonSet), baro-ai

### 배포 방식

- **데이터 인프라**: Docker Compose로 직접 배포
- **애플리케이션**: k3s를 통해 Kubernetes Pod로 배포
- **CI/CD**: 각 서버에 설치된 GitHub Actions Runner가 자동 배포 수행

### Kustomize를 활용한 배포

이 프로젝트는 **Kustomize**를 사용하여 Kubernetes 매니페스트를 선언적으로 관리합니다.

**주요 특징:**
- 선언적 관리: `kustomization.yaml` 파일을 통해 리소스 관리
- 이미지 태그 관리: `images` 섹션을 통해 이미지 태그 쉽게 변경
- 네이티브 지원: kubectl에 내장되어 있어 별도 설치 불필요

**디렉토리 구조:**
```
k8s/
├── base/                    # 기본 리소스
├── cloud/                   # Spring Cloud 인프라 모듈
│   ├── eureka/
│   ├── config/
│   └── gateway/
├── apps/                    # 비즈니스 애플리케이션 모듈
│   ├── baro-auth/
│   ├── baro-buyer/
│   ├── baro-seller/
│   ├── baro-order/
│   ├── baro-payment/
│   ├── baro-support/
│   ├── baro-settlement/     # DaemonSet
│   └── baro-ai/
└── redis/                   # Redis 캐시
```

---

## 사전 준비사항

### 1. GitHub Container Registry 설정

**GitHub에서 자동으로 이미지 저장소가 생성됩니다.**

```bash
# 이미지는 다음 형식으로 저장됩니다:
ghcr.io/{github-username}/{service-name}:tag

# 예시:
ghcr.io/do-develop-space/baro-auth:latest
ghcr.io/do-develop-space/baro-buyer:latest
ghcr.io/do-develop-space/baro-settlement:latest
ghcr.io/do-develop-space/eureka:latest
...
```

**이미지 공개 설정 (자동):**
✅ **CI/CD 파이프라인에서 자동으로 패키지를 public으로 설정합니다!**

이미지를 push한 후 자동으로 GitHub API를 통해 패키지 visibility를 public으로 변경합니다.
따라서 별도로 수동 설정할 필요가 없습니다.

**참고:**
- ✅ Public 패키지는 인증 없이 pull 가능
- ✅ 모든 서비스 패키지가 자동으로 public으로 설정됨

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
| `GITHUB_TOKEN` | GitHub Container Registry 인증 (GitHub에서 자동 제공) | ✅ 자동 |
| `GHCR_PAT` | GHCR 이미지 Push/Pull용 Personal Access Token (필요 시) | ⚠️ 선택 |
| `AWS_ACCESS_KEY` | AWS Access Key (S3, 기타 AWS 리소스 접근) | ✅ 필수 |
| `AWS_SECRET_KEY` | AWS Secret Key (S3, 기타 AWS 리소스 접근) | ✅ 필수 |
| `DATA_EC2_IP` | Data EC2 Private IP (MySQL/Redis/Kafka/ES 접속용) | ✅ 필수 |
| `EC2_HOST` | EC2 Public IP (SSH 접속, 수동 디버깅용) | ⚠️ 선택 |
| `EC2_USERNAME` | EC2 SSH 사용자명 (예: `ubuntu`) | ⚠️ 선택 |
| `EC2_SSH_KEY` | EC2 SSH Private Key (.pem 파일 내용) | ⚠️ 선택 |
| `TOSS_SECRET_KEY` | Toss Payments Secret Key (결제 모듈에서 사용) | ✅ 필수 |

**참고:** 
- `GITHUB_TOKEN`은 GitHub Actions가 자동으로 제공하므로 별도 설정이 필요 없습니다.
- Self-hosted Runner를 사용하므로 SSH 관련 Secret(EC2_HOST, EC2_USERNAME, EC2_SSH_KEY)은 **디버깅/수동 작업용 선택사항**입니다.

### GitHub Variables (환경 변수)

GitHub Repository → Settings → Secrets and variables → Actions → Repository variables

| Variable | 예시 값 | 설명 |
|----------|---------|------|
| `DEPLOY_KAFKA` | `true` / `false` | k3s 환경에서 Kafka를 배포할지 여부 (Docker Compose 기반 Kafka 사용 제어) |
| `DEPLOY_ELASTICSEARCH` | `true` / `false` | k3s 환경에서 Elasticsearch를 배포할지 여부 (Docker Compose 기반 ES 사용 제어) |

---

## AWS EC2 설정

### 1. Security Group 설정

**Inbound Rules:**

| Type | Protocol | Port Range | Source | Description |
|------|----------|-----------|--------|-------------|
| SSH | TCP | 22 | My IP | SSH 접속 |
| Custom TCP | TCP | 8080 | 0.0.0.0/0 | Gateway |
| Custom TCP | TCP | 8761 | 0.0.0.0/0 | Eureka Dashboard |
| Custom TCP | TCP | 8888 | 0.0.0.0/0 | Config Server |
| Custom TCP | TCP | 8081 | 0.0.0.0/0 | baro-auth |
| Custom TCP | TCP | 8082 | 0.0.0.0/0 | baro-buyer |
| Custom TCP | TCP | 8085 | 0.0.0.0/0 | baro-seller |
| Custom TCP | TCP | 8087 | 0.0.0.0/0 | baro-order |
| Custom TCP | TCP | 8088 | 0.0.0.0/0 | baro-payment |
| Custom TCP | TCP | 8089 | 0.0.0.0/0 | baro-support |
| Custom TCP | TCP | 8090 | 0.0.0.0/0 | baro-settlement |
| Custom TCP | TCP | 8092 | 0.0.0.0/0 | baro-ai |

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

# 2. dev-{모듈} 브랜치에 Push
git push origin dev-auth

# 3. main-{모듈} 브랜치에 PR 생성 및 머지

# 4. GitHub Actions Runner 자동 실행 (각 서버에서 실행)
- 코드 품질 검사 (Spotless, Checkstyle)
- 빌드 및 테스트
- Docker 이미지 빌드
- GHCR (GitHub Container Registry)에 이미지 푸시
- k3s 클러스터에 배포 (kubectl apply -k)

# 5. 배포 확인
# http://your-ec2-ip:8761 (Eureka Dashboard)
# http://your-ec2-ip:8080 (API Gateway)
```

### 수동 배포 (k3s 클러스터에서 직접)

```bash
# Master Node 또는 Worker Node 접속
ssh -i your-key.pem ubuntu@ec2-ip

# 최신 이미지 Pull (필요시)
docker pull ghcr.io/do-develop-space/baro-auth:latest

# k8s 배포 (Kustomize 사용)
cd /path/to/repo
kubectl apply -k k8s/apps/baro-auth/

# 배포 상태 확인
kubectl get pods -n baro-prod -l app=baro-auth

# Pod 로그 확인
kubectl logs -n baro-prod -l app=baro-auth --tail=100 -f

# 이미지 태그 업데이트 (kustomization.yaml 수정 후)
kubectl apply -k k8s/apps/baro-auth/
```

### 배포 스크립트 사용

```bash
# 자동 배포 스크립트 사용
./scripts/deploy-k8s.sh baro-auth

# 스크립트 기능:
# - EC2 Private IP 자동 감지
# - Deployment 파일에 EC2 IP 자동 설정
# - 이미지 태그 자동 업데이트
# - k8s 리소스 적용 및 상태 확인
```

---

## 배포 확인

### 1. Pod 상태 확인

```bash
# k3s 클러스터에서 실행
kubectl get pods -n baro-prod

# 모든 Pod가 "Running" 상태여야 함
NAME                          READY   STATUS    RESTARTS   AGE
baro-auth-xxx                 1/1     Running   0          5m
baro-buyer-xxx                1/1     Running   0          4m
baro-settlement-xxx           1/1     Running   0          3m
...

# 특정 서비스 Pod 확인
kubectl get pods -n baro-prod -l app=baro-auth
```

### 2. Health Check

```bash
# Eureka Dashboard
http://your-ec2-ip:8761

# Gateway Health Check
curl http://your-ec2-ip:8080/actuator/health

# 각 서비스 Health Check
curl http://your-ec2-ip:8081/actuator/health  # Auth
curl http://your-ec2-ip:8082/actuator/health  # Buyer
curl http://your-ec2-ip:8085/actuator/health  # Seller
curl http://your-ec2-ip:8087/actuator/health  # Order
curl http://your-ec2-ip:8088/actuator/health  # Payment
curl http://your-ec2-ip:8089/actuator/health  # Support
curl http://your-ec2-ip:8090/actuator/health  # Settlement
curl http://your-ec2-ip:8092/actuator/health  # AI
```

### 3. 로그 확인

```bash
# 특정 Pod 로그 확인
kubectl logs -n baro-prod -l app=baro-auth --tail=100 -f

# 모든 Pod 로그 확인
kubectl logs -n baro-prod --all-containers=true --tail=100

# 특정 Pod 이름으로 로그 확인
kubectl logs -n baro-prod baro-auth-xxx -f
```

### 4. 배포 상태 상세 확인

```bash
# Deployment 상태 확인
kubectl get deployments -n baro-prod

# Service 상태 확인
kubectl get services -n baro-prod

# DaemonSet 상태 확인 (baro-settlement)
kubectl get daemonsets -n baro-prod

# 이벤트 확인
kubectl get events -n baro-prod --sort-by='.lastTimestamp'
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
kubectl logs -n baro-prod -l app=baro-auth --tail=100

# Pod 상태 상세 확인
kubectl describe pod -n baro-prod -l app=baro-auth

# 일반적인 원인:
# - Eureka 서버 연결 실패
# - MySQL/Redis/Kafka 연결 실패
# - 환경 변수 누락
# - 메모리 부족

# 순서대로 배포
kubectl apply -k k8s/cloud/eureka/
kubectl wait --for=condition=ready pod -l app=eureka -n baro-prod --timeout=300s

kubectl apply -k k8s/cloud/config/
kubectl wait --for=condition=ready pod -l app=config -n baro-prod --timeout=300s

kubectl apply -k k8s/cloud/gateway/

kubectl apply -k k8s/apps/baro-auth/
kubectl apply -k k8s/apps/baro-buyer/
kubectl apply -k k8s/apps/baro-seller/
kubectl apply -k k8s/apps/baro-order/
kubectl apply -k k8s/apps/baro-payment/
kubectl apply -k k8s/apps/baro-support/
kubectl apply -k k8s/apps/baro-settlement/
kubectl apply -k k8s/apps/baro-ai/
```

### 5. 메모리 부족

**문제:** EC2 메모리 부족으로 Pod 종료

```bash
# 메모리 사용량 확인
free -h
kubectl top nodes
kubectl top pods -n baro-prod

# 해결방법:
# 1. EC2 인스턴스 타입 업그레이드 (t3.medium → t3.large)
# 2. 일부 서비스만 배포
# 3. JVM 메모리 설정 조정 (deployment.yaml에서 JAVA_OPTS)
# 4. Pod 리소스 제한 조정
```

### 6. 이미지 Pull 실패

**문제:** GHCR에서 이미지를 가져올 수 없음

```bash
# 이미지 Pull 테스트
docker pull ghcr.io/do-develop-space/baro-auth:latest

# 인증 필요 시
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# k3s에서 이미지 Pull 설정
# /etc/rancher/k3s/registries.yaml 파일 생성
```

### 7. DaemonSet 배포 문제 (baro-settlement)

**문제:** DaemonSet이 모든 노드에 배포되지 않음

```bash
# DaemonSet 상태 확인
kubectl get daemonsets -n baro-prod

# 특정 노드에 스케줄링되지 않는 경우
kubectl describe daemonset baro-settlement -n baro-prod

# Node Affinity 및 Taint 확인
kubectl get nodes --show-labels
kubectl describe node <node-name>
```

---

## 고급 설정

### 롤링 업데이트

k3s는 기본적으로 롤링 업데이트를 지원합니다:

```yaml
# deployment.yaml
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
# kustomization.yaml에서 이미지 태그 변경
cd k8s/apps/baro-auth
# images 섹션의 newTag를 변경

# 배포
kubectl apply -k .

# 이전 버전으로 롤백
# kustomization.yaml에서 이전 태그로 변경 후
kubectl apply -k .
```

### 버전 관리

자동 생성되는 이미지 태그:
```
ghcr.io/do-develop-space/baro-auth:
├── latest                         # 최신 버전
├── main-auth                      # 브랜치명
├── main-auth-abc123d              # 브랜치-커밋SHA
└── main-auth-20241205-143022      # 브랜치-타임스탬프
```

### 리소스 모니터링

```bash
# 노드 리소스 확인
kubectl top nodes

# Pod 리소스 확인
kubectl top pods -n baro-prod

# 특정 Pod 상세 정보
kubectl describe pod <pod-name> -n baro-prod
```

---

## 참고 자료

- [GitHub Actions 공식 문서](https://docs.github.com/actions)
- [k3s 공식 문서](https://k3s.io/)
- [Kubernetes 공식 문서](https://kubernetes.io/docs/)
- [Kustomize 공식 문서](https://kustomize.io/)
- [Docker 공식 문서](https://docs.docker.com/)
- [AWS EC2 사용자 가이드](https://docs.aws.amazon.com/ec2/)
- [Self-hosted Runner 가이드](./SELF_HOSTED_RUNNER_GUIDE.md)
- [k8s 배포 가이드](./K8S_DEPLOYMENT_GUIDE.md)

