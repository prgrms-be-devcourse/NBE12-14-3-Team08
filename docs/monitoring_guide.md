# 팀원 공유용 성능 테스트 구동 가이드

이 문서는 Docker Compose로 Prometheus와 Grafana를 실행하고, k6로 부하 테스트를 수행한 뒤 결과를 모니터링하는 방법을 설명합니다.

## 1. 사전 준비

- Docker Desktop 또는 Docker Engine 및 Docker Compose 설치
- 프로젝트 루트 디렉터리에서 명령어 실행
- 모니터링 설정 파일 준비
  - `docker-compose.monitoring.yml`
  - `prometheus/prometheus.yml`
  - `grafana/datasources/` 하위의 Prometheus 데이터 소스 설정 파일
  - `k6/load-test.js`

## 2. 모니터링 인프라 실행

프로젝트 루트에서 실행합니다.

```bash
docker compose -f docker-compose.monitoring.yml up -d prometheus grafana
```

Prometheus와 Grafana만 백그라운드로 실행합니다. k6는 부하 테스트를 실행할 때 별도로 실행하므로 여기서는 시작하지 않습니다.

### 실행 상태 확인

```bash
docker compose -f docker-compose.monitoring.yml ps
```

Prometheus와 Grafana가 실행 중인지 확인합니다.

### 접속 주소

| 서비스 | 주소 | 초기 로그인 정보 |
|---|---|---|
| Prometheus | http://localhost:9090 | 인증 없음 |
| Grafana | http://localhost:3000 | `admin` / `admin` |

Grafana는 Provisioning 설정을 통해 Prometheus 데이터 소스를 자동으로 등록합니다. Grafana에 로그인한 뒤 **Connections → Data sources**에서 Prometheus가 등록되어 있는지 확인합니다.

최초 로그인 시 비밀번호 변경을 요구할 수 있습니다.

## 3. k6 버전 확인

프로젝트에서 사용하는 k6 버전을 확인합니다.

```bash
docker compose -f docker-compose.monitoring.yml run --rm k6 version
```

현재 확인된 버전은 `k6 v2.3.0`입니다.

## 4. k6 부하 테스트 실행

> **주의:** 실제 API 테스트를 시작하기 전에 테스트 스크립트의 인증 토큰, 요청 데이터, 응답 검증 및 테스트 대상 URL을 확인해야 합니다. 아래 명령은 설정된 스크립트를 실행하는 명령이며, API 호출 성공을 보장하지는 않습니다.

Prometheus Remote Write로 결과를 전송하면서 실행합니다.

```bash
docker compose -f docker-compose.monitoring.yml run --rm \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write \
  k6 run -o experimental-prometheus-rw /k6/load-test.js
```

명령어 설명:

- `run --rm k6`: k6 컨테이너를 일회성으로 실행하고 종료 후 컨테이너를 제거합니다.
- `K6_PROMETHEUS_RW_SERVER_URL`: 메트릭을 받을 Prometheus Remote Write 주소입니다.
- `-o experimental-prometheus-rw`: k6 테스트 결과를 Prometheus Remote Write 형식으로 전송합니다.
- `/k6/load-test.js`: 호스트의 `k6/load-test.js`를 컨테이너 내부에서 읽는 경로입니다.

**중요:** k6는 Prometheus와 같은 Docker Compose 네트워크에서 실행되므로, 컨테이너 간 통신 주소로 `localhost:9090`이 아니라 `prometheus:9090`을 사용합니다.

현재 테스트 스크립트는 `BASE_URL`을 지정하지 않으면 `http://host.docker.internal:8080`을 대상으로 합니다. 따라서 Spring Boot 애플리케이션이 해당 주소에서 접근 가능하도록 실행되어 있어야 합니다. Linux 환경에서는 `host.docker.internal` 접근을 위한 추가 설정이 필요할 수 있습니다.

## 5. 테스트 결과 확인

### Prometheus

http://localhost:9090 에 접속합니다.

상단의 쿼리 입력란에서 다음과 같이 k6 메트릭을 조회할 수 있습니다.

```promql
k6_vus
```

```promql
k6_http_reqs
```

메트릭이 아직 표시되지 않는다면 다음을 확인합니다.

1. k6 테스트가 실제로 실행되었는지
2. k6 실행 로그에 Remote Write 오류가 있는지
3. Prometheus가 실행 중인지
4. Prometheus에 Remote Write 수신 기능이 활성화되어 있는지

현재 설정은 `--web.enable-remote-write-receiver` 옵션을 사용합니다.

### Grafana

http://localhost:3000 에 접속하고, Explore에서 Prometheus 데이터 소스를 선택한 뒤 같은 메트릭을 조회합니다.

예를 들어 다음 쿼리로 가상 사용자 수를 확인할 수 있습니다.

```promql
k6_vus
```

k6 메트릭이 들어오는 것을 확인한 후에는 필요한 지표를 패널로 구성하거나 k6용 대시보드를 추가할 수 있습니다.

## 6. 종료 방법

부하 테스트가 끝나면 일회성 k6 컨테이너는 자동으로 제거됩니다.

모니터링 인프라를 종료하려면 다음을 실행합니다.

```bash
docker compose -f docker-compose.monitoring.yml down
```

이 명령은 모니터링 Compose의 컨테이너와 네트워크를 종료합니다. **`-v` 옵션은 사용하지 마세요.** 데이터 볼륨을 유지하기 위한 것입니다.

PostgreSQL은 별도 Compose로 관리하므로 위 명령으로 PostgreSQL을 종료하지 않습니다.

## 7. 주의 사항

- 실제 부하 테스트 전에 대상 서버의 URL과 인증 방식을 확인합니다.
- 테스트 데이터가 실제 DB에 생성될 수 있으므로 테스트 환경을 사용하거나 데이터 정리 계획을 마련합니다.
- `http_req_duration`과 같은 응답 시간 지표뿐 아니라 요청 성공률, 오류율, 서버 CPU·메모리 및 DB 상태도 함께 확인합니다.
- k6의 Prometheus Remote Write 출력은 실험적 기능이므로 향후 k6 버전에서 동작이나 설정이 바뀔 수 있습니다.
