# Backend Development & Test Guide

본 프로젝트 백엔드(Spring Boot)의 상세 구동 환경 구성, 테스트 격리 전략 및 k6 기반 부하 테스트 명세입니다.

---

## 로컬 개발 환경 구축 (Local Infrastructure)

### 1. PostgreSQL 데이터베이스 구성
로컬 개발 환경과 통합 테스트 환경은 모두 Docker 컨테이너를 기반으로 격리되어 있습니다.

* **로컬 DB 연결 정보:** `localhost:5432` / 데이터베이스 및 계정명: `naegi`
* **시드 데이터 주입:** `local` 프로필 활성화 시 시연 및 초기 개발 편의성을 위한 샘플 데이터가 자동으로 인메모리 스크립트를 통해 주입됩니다.
* **외부 연동:** 사진 업로드(습관/벌칙 인증 이미지 보관) 기능 인터페이스를 완전히 테스트하려면 로컬 스토리지 또는 Supabase 인증용 `SUPABASE_SERVICE_ROLE_KEY` 환경변수가 주입되어 있어야 합니다.

### 2. 로컬 인프라 제어 명령어
```bash
# 컨테이너 실행
docker compose up -d

# 개발 데이터 및 볼륨을 완전히 초기화하고 재시작할 경우
docker compose down -v && docker compose up -d
```

---

## 테스트 자동화 전략 (Testing Strategy)

### 1. Testcontainers 기반 데이터베이스 인프라 격리
통합 테스트 실행 시 로컬 개발 데이터베이스 환경에 영향을 주지 않기 위해 **Testcontainers**를 도입했습니다.

```bash
cd backend
./gradlew test
```

* **동작 원리:** 테스트 코드 실행 시 Docker를 통해 테스트 전용 PostgreSQL 컨테이너를 동적으로 생성하고 소멸시킵니다. 개발 중인 로컬 데이터 테이블을 오염시키지 않으므로 멱등성 있는 테스트가 보장됩니다.
* **IDE 실행 환경 주의사항:** Gradle 명령어로 실행할 때는 `test` 프로필이 자동 적용됩니다. 다만 IntelliJ 등 IDE 환경에서 JUnit 러너로 개별 테스트 메서드를 구동할 때는 VM 옵션에 `-Dspring.profiles.active=test` 설정을 추가해야 정상 동작합니다.

---

## k6 기반 시스템 부하 테스트 (Load Testing)

오픈소스 부하 테스트 툴인 **k6**를 활용하여 주요 API 엔드포인트의 성능 한계와 인프라의 안정성을 검증합니다.

### 1. 부하 테스트 실행 방법
[k6 공식 가이드](https://grafana.com)에 따라 CLI 설치를 완료한 후, 로컬 또는 타겟 서버를 구동한 상태에서 프로젝트 루트 기준으로 실행합니다.

```bash
k6 run k6/smoke.js
```

### 2. 부하 테스트 시나리오 및 SLA(성능 목표) 명세
* **검증 대상 엔드포인트:** 공개 연결 확인 및 헬스체크 API (`/api/connection-test`)
* **타겟 환경 분리:** 로컬(`localhost:8080`)이 아닌 외부 스테이징/운영 인프라 서버를 대상으로 부하를 발생시킬 경우 `BASE_URL` 환경변수를 주입합니다.
  ```bash
  BASE_URL=https://example.com k6 run k6/smoke.js
  ```
* **워크로드 모델 (Workload Profile):** 30초 동안 가상 사용자(VU)를 최대 5명까지 점진적으로 선형 증가(Ramp-up)시킨 후, 1분간 해당 최고 부하 상태를 유지(Sustain)하며 병목 구간을 탐색합니다.
* **통과 기준 (SLA):** 시스템 안정성 검증을 위해 **요청 실패율 1% 미만**, **p95 응답 시간 500ms 미만** 달성을 자동 통과 조건으로 정의했습니다.