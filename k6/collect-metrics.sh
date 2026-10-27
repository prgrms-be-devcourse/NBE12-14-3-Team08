#!/usr/bin/env bash
# 부하 테스트 중 일정 간격(INTERVAL초, 기본 0.2)마다 서버·DB 상태를 CSV로 기록한다. Ctrl+C로 멈춘다.
#   - 커넥션 풀: JMX로 읽은 Hikari 사용 중(active) / 연결을 기다리는 요청(pending)
#   - 잠금 대기: PostgreSQL에서 잠금 때문에 기다리는 세션 수
# 실제 기록은 k6/MetricsCollector.java가 한다(JDK만 있으면 빌드 없이 실행된다).
#
# 실행 예: INTERVAL=0.2 k6/collect-metrics.sh k6/results/metrics.csv
# 서버는 JMX 옵션과 loadtest 프로필로 켜져 있어야 한다. (명령은 application-loadtest.yaml 위쪽 주석 참고)
set -euo pipefail

exec java "$(dirname "$0")/MetricsCollector.java" "${1:-metrics.csv}"
