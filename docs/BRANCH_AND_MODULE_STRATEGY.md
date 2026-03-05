## 브랜치별 모듈 관리 전략 가이드

### 1. 개요

이 문서는 `main-{모듈}` 브랜치에서 **해당 모듈 외 나머지 모듈을 코드 레벨에서 삭제하지 말아야 하는 이유**와,  
멀티모듈/모노레포 구조에서 **안전하게 MSA 스타일로 운영하는 방법**을 정리한 가이드입니다.

대상 예시:
- `main-user` 브랜치에서 `baro-user` 만 남기고 `baro-shopping`, `baro-order` 등을 삭제하고 싶은 욕구
- `main-order` 브랜치에서 `baro-order` 외 나머지 모듈 디렉토리를 지우고 싶은 케이스

결론부터 말하면, **현재 레포 구조에서는 “브랜치마다 자기 모듈 외 코드를 삭제하는 전략은 금지”**입니다.  
대신, **CI/CD 와 Gradle 설정으로 “해당 브랜치에 해당하는 모듈만 빌드·배포” 하도록 제어**하는 것이 맞는 방향입니다.

---

### 2. 현재 구조 요약


- **Gradle 멀티모듈(모노레포)** 구조
  - `settings.gradle` 에서 모든 모듈을 명시적으로 포함
    - 비즈니스 모듈: `baro-user`, `baro-shopping`, `baro-order`, `baro-payment`, `baro-notification`, `baro-settlement`, `baro-ai`, `baro-sample`
    - 인프라 모듈: `baro-gateway`, `baro-config`, `baro-eureka`, `baro-opa-bundle`
    - 공통: `baro-common`
- **CI/CD (모듈별 워크플로우 또는 공통 파이프라인)**
  - **path 기반** 트리거(예: `baro-gateway/**`, `k8s/apps/baro-gateway/**`) 또는 브랜치 이름(`main-user`, `main-order` 등)을 기준으로 **빌드/도커/배포 대상 모듈만 선택**
  - Gradle 프로젝트 구성은 **“전체 멀티모듈 프로젝트가 존재한다”**는 전제를 사용

즉, **코드(모듈)는 한 레포에 모두 모여 있고, 어떤 브랜치에서 어떤 모듈만 빌드/배포할지는 CI/CD 에서 제어하는 구조**입니다.

---

### 3. “브랜치별로 자기 모듈 외 나머지 코드 삭제” 시도 시 문제점

#### 3.1 Gradle 멀티모듈 구조 붕괴

- `settings.gradle` 예시:
  - `include 'baro-user', 'baro-shopping', 'baro-order', 'baro-payment', 'baro-notification', 'baro-settlement', 'baro-ai', 'baro-sample'`
  - `include 'baro-gateway', 'baro-config', 'baro-eureka', 'baro-opa-bundle', 'baro-common'`
- 만약 `main-user` 브랜치에서:
  - `baro-user` 를 제외한 다른 디렉터리(`baro-shopping`, `baro-order`, …)를 물리적으로 삭제하면,
  - Gradle 은 프로젝트 구성 단계에서 `:baro-shopping`, `:baro-order` 등을 찾다가 **즉시 실패**합니다.
- 이 에러는:
  - 개별 Task 실행 시점이 아니라, **`./gradlew` 를 실행하는 모든 경우에 공통적으로 발생**합니다.

**요약**:  
멀티모듈에서는 `settings.gradle` 과 실제 디렉터리 구조가 항상 일치해야 합니다.  
브랜치마다 모듈 디렉터리를 삭제하면 **어떤 작업이든 Gradle 설정 단계에서 깨지는 레포**가 됩니다.

#### 3.2 공통 CI/CD 파이프라인 전제 깨짐

- 현재 CI/CD 파이프라인은:
  - **“모듈은 모두 존재한다”** 는 전제 하에,
  - 브랜치 이름을 기준으로 **빌드/테스트/배포할 모듈만 선택**합니다.
- 예:
  - `main-user` 또는 path `baro-user/**` → `:baro-user` 만 빌드/배포
  - `main-order` 또는 path `baro-order/**` → `:baro-order` 만 빌드/배포
- 하지만, 프로젝트 구성 자체는 여전히 전체 멀티모듈을 기준으로 동작합니다.
  - 없는 모듈이 `settings.gradle` 에 남아 있으면,  
    CI/CD 의 어느 Job 이든 Gradle 설정 단계에서 실패할 수 있습니다.

**요약**:  
CI/CD 는 “필요한 모듈만 사용”하도록 설계되어 있지만,  
이는 **“모든 모듈이 레포 안에 존재한다”는 전제**가 있을 때만 안전합니다.  
코드를 삭제하면 이 전제 자체가 깨집니다.

#### 3.3 공통 변경/보안 패치 적용 난이도 폭증

- 현재의 장점:
  - 공통 설정/라이브러리 버전 업, 보안 패치, 스크립트 수정 등을 **한 번에 전체 모듈에 적용** 가능.
- 브랜치마다 모듈을 삭제하는 전략을 쓰면:
  - `main-user`, `main-shopping`, `main-order`, … 각 브랜치에 **동일한 수정 내용을 반복 적용**해야 합니다.
  - 시간이 지날수록 브랜치 간 설정/코드가 **서로 다른 방향으로 드리프트(drift)** 하게 됩니다.

**요약**:  
브랜치별로 모듈을 물리적으로 쪼개는 전략은,  
사실상 **“모듈별 별도 레포를 여러 개 운영하는 것과 비슷한 부담”** 을 가져오며,  
하나의 레포를 공유하는 이점이 거의 사라집니다.

#### 3.4 도메인/코드 가시성 및 개발 경험 저하

- 단일 레포 + 멀티모듈 구조의 장점:
  - IDE/도구에서 **전역 검색 / 전역 리팩터링 / 참조 관계 파악**이 쉽다.
  - 도메인 간 연관(예: order ↔ buyer, support ↔ data)을 한 눈에 볼 수 있다.
- 브랜치마다 “필요 없는 모듈을 삭제”하면:
  - 어떤 브랜치에서는 참조가 존재하고, 다른 브랜치에서는 동일 참조가 사라진 상태가 된다.
  - 전역적인 영향 범위를 파악하기 어려워지고,  
    리팩터링/분석 도구의 신뢰성이 떨어진다.

**요약**:  
브랜치별 코드 삭제는 “반쪽짜리 코드 뷰”를 양산하여,  
멀티모듈/모노레포의 장점을 직접적으로 훼손합니다.

---

### 4. 우리가 이미 가지고 있는 해법: “코드는 같이 두고, 빌드/배포만 모듈별로”

현재 레포는 이미 다음과 같은 설계를 갖추고 있습니다.

- **Gradle**
  - 각 모듈(`baro-user`, `baro-shopping`, `baro-order`, `baro-payment`, `baro-notification`, `baro-settlement`, `baro-ai`, `baro-gateway` 등)이  
    **자립형 `build.gradle`** 을 가짐
  - 개별 모듈만 `:baro-user:bootJar` 처럼 빌드 가능
- **CI/CD (모듈별 워크플로우 또는 공통 파이프라인)**
  - **path 기반** 트리거 또는 브랜치 이름 `main-{모듈명}` 을 기준으로:
    - 어떤 모듈만 빌드/테스트할지 결정
    - 해당 서비스 Docker 이미지를 빌드하여 ECR 푸시
    - k3s 클러스터에 **해당 모듈만 배포**

즉, **코드를 삭제하지 않고도 “해당 브랜치 = 해당 모듈만 빌드/배포”가 이미 구현되어 있는 상태**입니다.

---

### 5. 권장 전략

1. **코드(모듈 디렉터리)는 모든 브랜치에서 그대로 유지한다.**
   - `baro-user`, `baro-shopping`, `baro-order`, `baro-payment`, `baro-notification`, `baro-settlement`, `baro-ai`, `baro-gateway`, `baro-eureka`, `baro-config` 등
2. **브랜치 명명 규칙으로 “어떤 모듈을 대상으로 하는지”만 구분한다.**
   - 예: `main-user`, `main-order`, `main-shopping`, `main-gateway` 등
3. **CI/CD(path 기반 또는 브랜치 기반)와 Gradle 설정으로 “어떤 모듈만 빌드/배포할지”를 제어한다.**
   - path 기반: `baro-gateway/**`, `k8s/apps/baro-gateway/**` 등 변경 시 해당 모듈만 배포
   - 브랜치 기반: `main-{모듈명}` Push 시 해당 모듈만 빌드/배포
4. **장기적으로 진짜 물리 분리를 원한다면, 레포를 모듈별로 분리하는 방향으로 설계한다.**
   - 예: `baro-user-be`, `baro-order-be` 등 별도 레포로 이관
   - 이 경우에도 공통 설정/코드 공유 전략(공용 Gradle 플러그인, Git submodule 등)을 별도로 설계

---

### 6. 결론

- **브랜치별로 자기 모듈 외 나머지 코드를 삭제하는 전략은,**  
  Gradle 멀티모듈 구조, 공통 CI/CD, 공통 설정/보안 패치, 개발 경험 측면에서 **리스크가 크고 유지보수가 어렵기 때문에 금지**합니다.
- 우리는 이미:
  - **모듈별 자립형 Gradle 설정**
  - **브랜치 이름 기반 모듈 선택 빌드/배포 파이프라인**
  을 갖추고 있으므로,
- 앞으로도 **코드는 한 레포에 함께 두되, 빌드/배포를 모듈 단위로 제어하는 전략**을 유지하는 것이 가장 안전하고 현실적인 운영 방법입니다.


