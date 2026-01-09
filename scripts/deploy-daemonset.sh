#!/bin/bash

# ===================================
# DaemonSet 배포 스크립트
# Usage: bash deploy-daemonset.sh [MODULE_NAME] [IMAGE_TAG]
# Example: bash deploy-daemonset.sh settlement latest
#          bash deploy-daemonset.sh baro-settlement main-settlement-abc123d
#
# 환경변수:
#   DATA_EC2_IP: Data EC2의 Private IP
#                - Data EC2와 k8s Worker EC2 분리에서는 필수
#                - 설정하지 않으면 현재 EC2(스크립트 실행 EC2)의 IP를 자동 감지
#                  (잘못된 IP가 감지되므로 반드시 설정 필요)
# Example: DATA_EC2_IP=10.0.1.100 bash deploy-daemonset.sh settlement latest
# ===================================

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 로그 함수
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# ===================================
# 파라미터 검증
# ===================================
MODULE_NAME=${1:-}
IMAGE_TAG=${2:-latest}

if [ -z "$MODULE_NAME" ]; then
    log_error "모듈 이름을 지정해주세요."
    echo "Usage: bash deploy-daemonset.sh [MODULE_NAME] [IMAGE_TAG]"
    echo ""
    echo "Available modules:"
    echo "  - settlement (정산 모듈)"
    exit 1
fi

# ===================================
# k8s 디렉토리 자동 탐색
# ===================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_BASE_DIR=""

# 여러 경로에서 k8s 디렉토리 찾기 (우선순위 순)
# GitHub Actions runner에서는 워크스페이스의 k8s 디렉토리를 우선 사용
# SSH 원격 실행 시에는 EC2 경로를 사용
if [ -d "$SCRIPT_DIR/../k8s/cloud" ]; then
    # 스크립트 기준 상대 경로 (GitHub Actions runner에서 가장 가능성 높음)
    K8S_BASE_DIR="$SCRIPT_DIR/../k8s"
elif [ -d "$SCRIPT_DIR/../../k8s/cloud" ]; then
    # 스크립트 기준 상위 상위 경로
    K8S_BASE_DIR="$SCRIPT_DIR/../../k8s"
elif [ -d "./k8s/cloud" ]; then
    # 현재 디렉토리 기준
    K8S_BASE_DIR="./k8s"
elif [ -d "/home/ubuntu/apps/k8s/cloud" ]; then
    # EC2 배포 기준 디렉토리 (SSH 원격 실행용)
    K8S_BASE_DIR="/home/ubuntu/apps/k8s"
elif [ -d "/home/ubuntu/apps/BE/k8s/cloud" ]; then
    # EC2 BE 디렉토리
    K8S_BASE_DIR="/home/ubuntu/apps/BE/k8s"
else
    log_error "k8s 디렉토리를 찾을 수 없습니다."
    log_error "다음 경로를 확인했습니다:"
    log_error "  - $SCRIPT_DIR/../k8s"
    log_error "  - $SCRIPT_DIR/../../k8s"
    log_error "  - ./k8s"
    log_error "  - /home/ubuntu/apps/k8s"
    log_error "  - /home/ubuntu/apps/BE/k8s"
    exit 1
fi

log_info "k8s 디렉토리: $K8S_BASE_DIR"

# ===================================
# kubectl 확인 및 테스트
# ===================================
log_step "🔍 Checking kubectl..."
KUBECTL_CMD=""

if command -v kubectl &> /dev/null; then
    if kubectl get nodes &> /dev/null 2>&1; then
        KUBECTL_CMD="kubectl"
        log_info "✅ 일반 kubectl 사용 가능"
    elif command -v k3s &> /dev/null; then
        if sudo k3s kubectl get nodes &> /dev/null 2>&1; then
            KUBECTL_CMD="sudo k3s kubectl"
            log_info "✅ sudo k3s kubectl 사용"
        fi
    fi
fi

if [ -z "$KUBECTL_CMD" ] && command -v k3s &> /dev/null; then
    if sudo k3s kubectl get nodes &> /dev/null 2>&1; then
        KUBECTL_CMD="sudo k3s kubectl"
        log_info "✅ sudo k3s kubectl 사용"
    fi
fi

if [ -z "$KUBECTL_CMD" ]; then
    log_error "kubectl 또는 k3s가 설치되어 있지 않거나 클러스터에 연결할 수 없습니다."
    exit 1
fi

log_info "📦 사용할 kubectl 명령어: $KUBECTL_CMD"

# ===================================
# Data EC2 Private IP 설정
# ===================================
log_step "🌐 Setting Data EC2 Private IP..."
DATA_EC2_IP="${DATA_EC2_IP:-}"

if [ -z "$DATA_EC2_IP" ]; then
    log_warn "⚠️  DATA_EC2_IP 환경변수가 설정되지 않았습니다."
    log_warn "⚠️  현재 EC2의 IP를 자동 감지합니다."
    
    DATA_EC2_IP=$(curl -s --max-time 2 http://169.254.169.254/latest/meta-data/local-ipv4 2>/dev/null || echo "")
    
    if [ -z "$DATA_EC2_IP" ]; then
        DATA_EC2_IP=$(hostname -I | awk '{print $1}' 2>/dev/null || echo "")
    fi
    
    if [ -z "$DATA_EC2_IP" ]; then
        DATA_EC2_IP=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7; exit}' || echo "")
    fi
    
    if [ -z "$DATA_EC2_IP" ]; then
        log_error "❌ IP를 자동으로 감지할 수 없습니다."
        exit 1
    fi
    log_info "✅ 현재 EC2 IP 자동 감지: $DATA_EC2_IP"
else
    log_info "✅ 환경변수 DATA_EC2_IP 사용: $DATA_EC2_IP"
fi

log_info "📍 Data EC2 Private IP: $DATA_EC2_IP"

# ===================================
# Namespace 생성
# ===================================
log_step "📦 Applying base resources (namespace)..."
if $KUBECTL_CMD apply -k "$K8S_BASE_DIR/base/"; then
    log_info "✅ Base resources (namespace) 적용 완료"
else
    log_error "❌ Base resources 적용 실패"
    exit 1
fi

# ===================================
# 모듈별 배포 경로 결정
# ===================================
case "$MODULE_NAME" in
    settlement|baro-settlement)
        DEPLOY_PATH="$K8S_BASE_DIR/apps/baro-settlement"
        APP_NAME="baro-settlement"
        ;;
    *)
        log_error "알 수 없는 모듈: $MODULE_NAME"
        log_info "사용 가능한 모듈: settlement"
        exit 1
        ;;
esac

# ===================================
# 배포 경로 확인
# ===================================
if [ ! -d "$DEPLOY_PATH" ]; then
    log_error "배포 경로를 찾을 수 없습니다: $DEPLOY_PATH"
    exit 1
fi

# ===================================
# kustomization.yaml에서 이미지 태그 업데이트
# ===================================
KUSTOMIZATION_FILE="$DEPLOY_PATH/kustomization.yaml"
if [ -f "$KUSTOMIZATION_FILE" ] && [ "$IMAGE_TAG" != "latest" ]; then
    log_step "🏷️  이미지 태그 업데이트: $IMAGE_TAG"
    # kustomization.yaml에서 이미지 태그 업데이트 (백업 파일 생성)
    if sed -i.bak "s|newTag: latest|newTag: ${IMAGE_TAG}|g" "$KUSTOMIZATION_FILE" 2>/dev/null || \
       sed -i "s|newTag: latest|newTag: ${IMAGE_TAG}|g" "$KUSTOMIZATION_FILE" 2>/dev/null; then
        # 백업 파일은 배포 후 정리 (또는 보존)
        # rm -f "${KUSTOMIZATION_FILE}.bak" 2>/dev/null || true
        log_info "✅ kustomization.yaml 이미지 태그 업데이트 완료 (백업 파일: ${KUSTOMIZATION_FILE}.bak)"
    else
        log_warn "⚠️  kustomization.yaml 이미지 태그 업데이트 실패, 기존 설정 사용"
    fi
fi

# ===================================
# DaemonSet 파일에 EC2 IP 설정 (임시 파일 사용)
# ===================================
DAEMONSET_FILE="$DEPLOY_PATH/daemonset.yaml"
TEMP_DAEMONSET=""

log_info "🔍 DaemonSet 파일 확인: $DAEMONSET_FILE"
if [ -f "$DAEMONSET_FILE" ]; then
    log_info "✅ DaemonSet 파일 존재 확인됨"
    TEMP_DAEMONSET=$(mktemp)
    cp "$DAEMONSET_FILE" "$TEMP_DAEMONSET"
    
    log_step "🔧 DaemonSet 파일 설정 중..."
    
    # Data EC2 IP 설정
    if grep -q "CHANGE_ME_TO_EC2_IP" "$TEMP_DAEMONSET"; then
        REPLACEMENT_IP="$DATA_EC2_IP"
        log_info "Data EC2 IP 설정 중 (CHANGE_ME_TO_EC2_IP -> $DATA_EC2_IP)"
        
        sed "s/CHANGE_ME_TO_EC2_IP/$REPLACEMENT_IP/g" "$TEMP_DAEMONSET" > "${TEMP_DAEMONSET}.tmp"
        mv "${TEMP_DAEMONSET}.tmp" "$TEMP_DAEMONSET"
        
        if grep -q "CHANGE_ME_TO_EC2_IP" "$TEMP_DAEMONSET"; then
            log_error "❌ IP 치환이 완료되지 않았습니다."
            grep -n "CHANGE_ME_TO_EC2_IP" "$TEMP_DAEMONSET" || true
            rm -f "$TEMP_DAEMONSET"
            exit 1
        else
            log_info "✅ IP 치환 완료: $REPLACEMENT_IP"
        fi
    fi
    
    # EC2_IP 환경 변수 설정
    if grep -q "name: EC2_IP" "$TEMP_DAEMONSET"; then
        if grep -q 'value: "CHANGE_ME_TO_EC2_IP"' "$TEMP_DAEMONSET" || grep -q 'value: "127.0.0.1"' "$TEMP_DAEMONSET"; then
            log_info "EC2_IP 환경 변수 설정 중 (Data EC2 IP: $DATA_EC2_IP)"
            sed "/name: EC2_IP/,/value:/ s|value: \".*\"|value: \"$DATA_EC2_IP\"|" "$TEMP_DAEMONSET" > "${TEMP_DAEMONSET}.tmp"
            mv "${TEMP_DAEMONSET}.tmp" "$TEMP_DAEMONSET"
        fi
    fi
    
    # SPRING_DATASOURCE_URL 업데이트
    if grep -q "SPRING_DATASOURCE_URL" "$TEMP_DAEMONSET"; then
        log_info "SPRING_DATASOURCE_URL 업데이트 중 (Data EC2 IP: $DATA_EC2_IP)"
        sed "s|\$(EC2_IP)|$DATA_EC2_IP|g" "$TEMP_DAEMONSET" > "${TEMP_DAEMONSET}.tmp"
        mv "${TEMP_DAEMONSET}.tmp" "$TEMP_DAEMONSET"
        sed "s|127\.0\.0\.1:3306|$DATA_EC2_IP:3306|g" "$TEMP_DAEMONSET" > "${TEMP_DAEMONSET}.tmp"
        mv "${TEMP_DAEMONSET}.tmp" "$TEMP_DAEMONSET"
    fi
    
    # Cloud 서비스 접근 URL 처리
    if [[ "$DEPLOY_PATH" == *"/apps/"* ]]; then
        log_info "애플리케이션 모듈 감지: Cloud 서비스 접근 URL을 Data EC2 IP로 변경"
        
        if grep -q "localhost:8888" "$TEMP_DAEMONSET"; then
            sed "s|http://localhost:8888|http://$DATA_EC2_IP:8888|g" "$TEMP_DAEMONSET" > "${TEMP_DAEMONSET}.tmp"
            mv "${TEMP_DAEMONSET}.tmp" "$TEMP_DAEMONSET"
            log_info "✅ SPRING_CONFIG_IMPORT: localhost:8888 → $DATA_EC2_IP:8888"
        fi
        
        if grep -q "localhost:8761" "$TEMP_DAEMONSET"; then
            sed "s|http://localhost:8761/eureka/|http://$DATA_EC2_IP:8761/eureka/|g" "$TEMP_DAEMONSET" > "${TEMP_DAEMONSET}.tmp"
            mv "${TEMP_DAEMONSET}.tmp" "$TEMP_DAEMONSET"
            log_info "✅ EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: localhost:8761 → $DATA_EC2_IP:8761"
        fi
    fi
    
    # 최종 검증: CHANGE_ME_TO_EC2_IP가 남아있는지 확인
    if grep -q "CHANGE_ME_TO_EC2_IP" "$TEMP_DAEMONSET"; then
        log_error "❌ 치환되지 않은 CHANGE_ME_TO_EC2_IP가 남아있습니다!"
        log_error "다음 위치에서 발견:"
        grep -n "CHANGE_ME_TO_EC2_IP" "$TEMP_DAEMONSET" || true
        log_error "배포를 중단합니다. 스크립트를 확인하세요."
        rm -f "$TEMP_DAEMONSET"
        exit 1
    fi
    
    # 임시 daemonset.yaml을 원본 위치에 복사 (kustomize가 읽을 수 있도록)
    # 원본 파일이 존재하면 백업 생성 (기존 파일 보존)
    if [ -f "$DAEMONSET_FILE" ]; then
        BACKUP_FILE="${DAEMONSET_FILE}.bak.$(date +%Y%m%d_%H%M%S)"
        cp "$DAEMONSET_FILE" "$BACKUP_FILE" 2>/dev/null || true
        log_info "💾 원본 파일 백업: $BACKUP_FILE"
    fi
    cp "$TEMP_DAEMONSET" "$DAEMONSET_FILE"
    rm -f "$TEMP_DAEMONSET"
    log_info "✅ DaemonSet 파일 업데이트 완료 (원본 파일 백업됨)"
else
    log_error "DaemonSet 파일을 찾을 수 없습니다: $DAEMONSET_FILE"
    exit 1
fi

# ===================================
# DaemonSet 이름 추출
# ===================================
DAEMONSET_NAME=""
if [ -f "$DAEMONSET_FILE" ]; then
    DAEMONSET_NAME=$(grep -E "^  name:" "$DAEMONSET_FILE" | head -1 | awk '{print $2}' 2>/dev/null)
    
    if [ -z "$DAEMONSET_NAME" ]; then
        DAEMONSET_NAME=$(grep -E "^\s+name:" "$DAEMONSET_FILE" | grep -v "namespace:" | head -1 | awk '{print $2}' 2>/dev/null)
    fi
    
    if [ -z "$DAEMONSET_NAME" ]; then
        DAEMONSET_NAME=$(sed -n '/^metadata:/,/^spec:/p' "$DAEMONSET_FILE" | grep "name:" | head -1 | awk '{print $2}' 2>/dev/null)
    fi
fi

if [ -z "$DAEMONSET_NAME" ] && [ -n "$APP_NAME" ]; then
    DAEMONSET_NAME="$APP_NAME"
    log_info "💡 DAEMONSET_NAME을 APP_NAME으로 설정: $DAEMONSET_NAME"
fi

# ===================================
# k8s 배포 (kustomize 사용)
# ===================================
log_step "📦 k8s 리소스 적용 중 (kustomize)..."
log_info "Applying resources from: $DEPLOY_PATH"
log_info "Data EC2 IP: $DATA_EC2_IP, Image Tag: $IMAGE_TAG"

set +e
APPLY_OUTPUT=$($KUBECTL_CMD apply -k "$DEPLOY_PATH" 2>&1)
APPLY_EXIT_CODE=$?
set -e

if [ $APPLY_EXIT_CODE -ne 0 ]; then
    log_error "❌ kubectl apply 실패 (exit code: $APPLY_EXIT_CODE)"
    log_error "출력:"
    echo "$APPLY_OUTPUT"
    
    if echo "$APPLY_OUTPUT" | grep -q "selector.*immutable\|field is immutable"; then
        log_warn "⚠️  DaemonSet selector 충돌 감지. 기존 DaemonSet을 삭제하고 재생성합니다..."
        
        if [ -z "$DAEMONSET_NAME" ] && [ -n "$APP_NAME" ]; then
            DAEMONSET_NAME="$APP_NAME"
        fi
        
        if [ -n "$DAEMONSET_NAME" ]; then
            log_info "기존 DaemonSet 삭제: $DAEMONSET_NAME"
            $KUBECTL_CMD delete daemonset "$DAEMONSET_NAME" -n baro-prod --ignore-not-found=true
            sleep 2
            log_info "DaemonSet 재생성 중..."
            
            set +e
            RECREATE_OUTPUT=$($KUBECTL_CMD apply -k "$DEPLOY_PATH" 2>&1)
            RECREATE_EXIT_CODE=$?
            set -e
            
            if [ $RECREATE_EXIT_CODE -eq 0 ]; then
                log_info "✅ DaemonSet 재생성 완료"
                echo "$RECREATE_OUTPUT"
            else
                log_error "❌ DaemonSet 재생성 실패"
                echo "$RECREATE_OUTPUT"
                exit 1
            fi
        else
            log_error "❌ DaemonSet 이름을 찾을 수 없습니다."
            exit 1
        fi
    else
        echo "$APPLY_OUTPUT"
        log_error "❌ 배포 실패. 에러를 확인하세요."
        exit 1
    fi
else
    echo "$APPLY_OUTPUT"
fi

# ===================================
# IMAGE_TAG가 latest일 때 rollout restart
# ===================================
if [ "$IMAGE_TAG" = "latest" ] && [ -n "$DAEMONSET_NAME" ]; then
    log_info "🔄 latest 태그 사용 중이므로 Pod 재시작 (rollout restart)..."
    $KUBECTL_CMD rollout restart daemonset/"$DAEMONSET_NAME" -n baro-prod || true
fi

# ===================================
# 배포 상태 확인
# ===================================
if [ -f "$DAEMONSET_FILE" ]; then
    if [ -n "$DAEMONSET_NAME" ]; then
        log_step "⏳ Pod가 Ready 상태가 될 때까지 대기 중..."
        if ! $KUBECTL_CMD wait --for=condition=ready pod -l app="$APP_NAME" -n baro-prod --timeout=300s 2>&1; then
            log_warn "⚠️ $APP_NAME Pod가 Ready 상태가 되지 않았습니다. 상태 확인 중..."
            echo ""
            echo "📊 $APP_NAME Pod 상태:"
            $KUBECTL_CMD get pods -n baro-prod -l app="$APP_NAME" 2>&1 || true
            echo ""
            echo "📋 $APP_NAME DaemonSet 상태:"
            $KUBECTL_CMD get daemonset "$DAEMONSET_NAME" -n baro-prod 2>&1 || true
            echo ""
            echo "📝 $APP_NAME Pod 이벤트:"
            LATEST_POD=$($KUBECTL_CMD get pods -n baro-prod -l app="$APP_NAME" --sort-by=.metadata.creationTimestamp -o jsonpath='{.items[-1].metadata.name}' 2>/dev/null || echo "")
            if [ -n "$LATEST_POD" ]; then
                $KUBECTL_CMD describe pod "$LATEST_POD" -n baro-prod 2>&1 | grep -A 30 "Events:" || true
                echo ""
                echo "📄 $APP_NAME Pod 로그 (마지막 50줄):"
                $KUBECTL_CMD logs "$LATEST_POD" -n baro-prod --tail=50 2>&1 || true
            fi
            log_warn "⚠️ $APP_NAME 배포는 계속 진행하지만, Pod가 준비되지 않았을 수 있습니다."
        else
            log_info "✅ $APP_NAME Pod가 Ready 상태입니다."
        fi
        
        log_info "✅ 배포 완료: $MODULE_NAME"
        $KUBECTL_CMD get pods -n baro-prod -l app="$APP_NAME"
    else
        log_info "✅ 리소스 적용 완료: $MODULE_NAME"
    fi
else
    log_info "✅ 리소스 적용 완료: $MODULE_NAME"
fi

log_info "🎉 DaemonSet deployment completed!"

